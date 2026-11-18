package app.moviebox.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class MovieBoxApi {

    private val hosts = listOf(
        "https://api6.aoneroom.com",
        "https://api5.aoneroom.com",
        "https://api4.aoneroom.com",
        "https://api4sg.aoneroom.com",
        "https://api3.aoneroom.com",
        "https://api6sg.aoneroom.com",
        "https://api.inmoviebox.com",
    )
    private var hostIndex = 0

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Stable MovieBox-app user agent reused for every CDN request. */
    private val userAgent: String = MovieBoxSigner.generateClientInfoAndUa().first

    @Volatile private var token: String? = null

    suspend fun ensureSession(): String {
        token?.let { if (it.isNotBlank()) return it }
        synchronized(this) {
            token?.let { if (it.isNotBlank()) return it }
            val body = JSONObject().apply {
                put("deviceInfo", JSONObject().apply {
                    put("deviceName", "moviebox-android")
                    put("deviceModel", "Redmi")
                    put("osVersion", 13)
                    put("appVersion", "4.0.01.0813.03")
                    put("channel", "ps")
                })
            }
            val raw = request("POST", "/wefeed-mobile-bff/user-api/visitor-login", body.toString(), currentHost() + "/wefeed-mobile-bff/user-api/visitor-login")
            val data = raw.optJSONObject("data") ?: raw
            val t = data.optString("token").ifBlank { data.optString("accessToken") }
            if (t.isNotBlank()) token = t
            return token ?: throw RuntimeException("visitor-login failed")
        }
    }

    private fun currentHost(): String = hosts[hostIndex % hosts.size]

    private fun withRetry(method: String, path: String, body: String?): JSONObject {
        val retryCodes = setOf(403, 406, 407, 429, 500, 502, 503, 504)
        var lastError: Exception? = null
        for (attempt in 0..hosts.size) {
            val url = currentHost() + path
            try {
                val raw = request(method, path, body, url)
                return raw
            } catch (e: HttpError) {
                lastError = e
                if (e.code in retryCodes && attempt < hosts.size - 1) {
                    hostIndex = (hostIndex + 1) % hosts.size
                    continue
                }
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < hosts.size - 1) {
                    hostIndex = (hostIndex + 1) % hosts.size
                    continue
                }
            }
        }
        throw lastError ?: RuntimeException("request failed")
    }

    private class HttpError(val code: Int, msg: String) : Exception(msg)

    private fun request(method: String, path: String, body: String?, url: String): JSONObject {
        val headers = MovieBoxSigner.buildHeaders(method, url, body, token)
        val builder = Request.Builder().url(url)
        headers.toMap().forEach { (k, v) -> builder.header(k, v) }
        token?.let { builder.header("Authorization", "Bearer $it") }
        val req = when (method) {
            "POST" -> builder.post(body?.toRequestBody("application/json".toMediaType()) ?: "{}".toRequestBody("application/json".toMediaType())).build()
            else -> builder.get().build()
        }
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw HttpError(resp.code, "HTTP ${resp.code}: $text")
            return JSONObject(text)
        }
    }

    // ---------------- public endpoints ----------------

    suspend fun search(query: String, page: Int = 1): List<CatalogItem> {
        ensureSession()
        val payload = JSONObject().apply {
            put("keyword", query)
            put("page", page)
            put("perPage", 15)
            put("subjectType", 0)
        }
        return parseCatalog(withRetry("POST", "/wefeed-mobile-bff/subject-api/search/v2", payload.toString()))
    }

    suspend fun homepage(tabId: String, page: Int = 1): List<CatalogItem> {
        ensureSession()
        val path = "/wefeed-mobile-bff/tab-operating?page=$page&tabId=$tabId&version="
        return parseCatalog(withRetry("GET", path, null))
    }

    suspend fun details(subjectId: String): MediaDetail {
        ensureSession()
        val path = "/wefeed-mobile-bff/subject-api/get?subjectId=$subjectId"
        val json = withRetry("GET", path, null)
        val data = json.optJSONObject("data") ?: json

        val stype = data.optInt("subjectType", data.optInt("stype", 1))
        var seasons: List<Season> = emptyList()
        if (stype == 2) {
            try {
                val si = withRetry("GET", "/wefeed-mobile-bff/subject-api/season-info?subjectId=$subjectId", null)
                seasons = parseSeasons(si.optJSONObject("data") ?: si)
            } catch (_: Exception) { }
        }
        return data.toMediaDetail(seasons)
    }

    /**
     * Resolves playable streams using the proven resolution order:
     * 1. details `resourceDetectors[].resolutionList[]` (real links for movies)
     * 2. H5 web play endpoint (`/wefeed-h5-bff/web/subject/play`) — the only
     *    source of real series links; needs the `detailUrl` slug as Referer
     * 3. play-info / resource fallback (both may only return the site trailer,
     *    which is filtered out)
     */
    suspend fun streams(subjectId: String, season: Int = 0, episode: Int = 0): List<Stream> {
        ensureSession()
        val releases = LinkedHashMap<String, Stream>()

        // 1. Detector resolutionList from the subject details.
        try {
            val details = withRetry("GET", "/wefeed-mobile-bff/subject-api/get?subjectId=$subjectId", null)
            parseDetectorReleases(details, season, episode).forEach { putStream(releases, it) }
        } catch (_: Exception) { }

        // 2. H5 web play endpoint — the only source of real series links.
        if (releases.isEmpty()) {
            try {
                val details = withRetry("GET", "/wefeed-mobile-bff/subject-api/get?subjectId=$subjectId", null)
                val slug = detailSlug(details)
                if (slug != null) {
                    val h5 = h5Play(subjectId, season, episode, slug)
                    parseH5Releases(h5, season, episode).forEach { putStream(releases, it) }
                }
            } catch (_: Exception) { }
        }

        // 3. Legacy fallbacks (play-info / resource) — filter the trailer clip.
        if (releases.isEmpty()) {
            val playPath = if (season == 0 && episode == 0)
                "/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$subjectId"
            else
                "/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$subjectId&se=$season&ep=$episode"
            val resourcePath = if (season == 0 && episode == 0)
                "/wefeed-mobile-bff/subject-api/resource?subjectId=$subjectId&page=1&perPage=20"
            else
                "/wefeed-mobile-bff/subject-api/resource?subjectId=$subjectId&se=$season&ep=$episode&page=1&perPage=20"
            parseReleases(withRetry("GET", playPath, null), season, episode, releases)
            parseReleases(withRetry("GET", resourcePath, null), season, episode, releases)
        }

        return releases.values.toList()
    }

    private fun putStream(out: MutableMap<String, Stream>, stream: Stream) {
        val key = stream.url.substringBefore('?')
        if (key.isNotBlank()) out.putIfAbsent(key, stream)
    }

    // ---------------- detector resolutionList (movies) ----------------

    private fun parseDetectorReleases(json: JSONObject, season: Int, episode: Int): List<Stream> {
        val data = json.optJSONObject("data") ?: json
        val detectors = data.optJSONArray("resourceDetectors") ?: return emptyList()
        val out = mutableListOf<Stream>()
        for (d in 0 until detectors.length()) {
            val detector = detectors.getJSONObject(d)
            val uploader = detector.optString("uploadBy").ifBlank { "MovieBox" }
            val list = detector.optJSONArray("resolutionList") ?: continue
            for (e in 0 until list.length()) {
                val entry = list.getJSONObject(e)
                if (season != 0 || episode != 0) {
                    val se = entry.optInt("se", -1)
                    val ep = entry.optInt("ep", -1)
                    if (se != season || ep != episode) continue
                }
                val url = entry.optString("resourceLink").ifBlank { entry.optString("url") }
                if (url.isBlank() || isTrailerUrl(url)) continue
                val resolution = entry.optInt("resolution", 0)
                val quality = when {
                    resolution >= 2160 -> "4K"
                    resolution >= 1080 -> "1080p"
                    resolution >= 720 -> "720p"
                    resolution >= 480 -> "480p"
                    else -> "Auto"
                }
                val sizeBytes = entry.optString("size").takeIf { it.isNotBlank() }?.toLongOrNull()
                val name = entry.optString("title").ifBlank { "$uploader $quality" }
                out.add(Stream(
                    name = name, quality = quality, url = url,
                    sizeMb = sizeBytes?.let { it / 1024.0 / 1024.0 },
                    resourceId = entry.optString("resourceId").ifBlank { null },
                    // The CDN expects the MovieBox app user agent and rejects a Referer.
                    headers = mapOf("User-Agent" to userAgent),
                ))
            }
        }
        return out
    }

    private fun detailSlug(json: JSONObject): String? {
        val data = json.optJSONObject("data") ?: json
        for (key in listOf("detailUrl", "detailPath")) {
            val raw = data.optString(key).trim()
            if (raw.isNotEmpty()) {
                val path = raw.substringBefore('?').substringBefore('#')
                val slug = path.trimEnd('/').substringAfterLast('/').trim()
                if (slug.isNotEmpty()) return slug
            }
        }
        return null
    }

    // ---------------- H5 web play endpoint (series) ----------------

    private fun h5Play(subjectId: String, season: Int, episode: Int, slug: String): JSONObject {
        val url = "https://h5.aoneroom.com/wefeed-h5-bff/web/subject/play" +
            "?subjectId=$subjectId&se=$season&ep=$episode"
        val referer = "https://h5.aoneroom.com/movies/$slug"
        val req = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Referer", referer)
            .header("Accept", "application/json")
            .header("X-Client-Info", """{"timezone":"UTC"}""")
            .get().build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException("H5 play HTTP ${resp.code}")
            return JSONObject(text)
        }
    }

    private fun parseH5Releases(json: JSONObject, season: Int, episode: Int): List<Stream> {
        val data = json.optJSONObject("data") ?: json
        val streams = data.optJSONArray("streams") ?: return emptyList()
        val title = data.optString("title").ifBlank { "MovieBox" }
        val out = mutableListOf<Stream>()
        for (i in 0 until streams.length()) {
            val s = streams.getJSONObject(i)
            val url = s.optString("url").trim()
            if (url.isBlank() || isTrailerUrl(url)) continue
            val resolutions = s.optString("resolutions").ifBlank { s.optString("resolution") }
            val first = resolutions.split(",").map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "stream"
            val quality = if (first.endsWith("p") || first.endsWith("P")) first else "$first" + "p"
            val sizeBytes = s.optString("size").takeIf { it.isNotBlank() }?.toLongOrNull()
            val name = if (season > 0 || episode > 0)
                "$title S${"%02d".format(season)}E${"%02d".format(episode)} $quality"
            else "$title $quality"
            out.add(Stream(
                name = name, quality = quality, url = url,
                sizeMb = sizeBytes?.let { it / 1024.0 / 1024.0 },
                resourceId = s.optString("id").ifBlank { s.optString("resourceId") }.ifBlank { null },
                // The H5 CDN answers 429 unless the request looks like the official web player.
                headers = mapOf(
                    "Referer" to "https://officialmoviebox.com/",
                    "User-Agent" to userAgent,
                ),
            ))
        }
        return out
    }

    // ---------------- trailer / deprecation-notice filter ----------------

    private fun isTrailerUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("1c7de0bd3393702d9191801f15f88f8d") ||
            lower.contains("9a0461bc39da389663bf3dbb17091d3f") ||
            lower.contains("b164fbfb4347792950bdfbfb563d39d9") ||
            lower.contains("/notice.mp4") ||
            (lower.contains("macdn.aoneroom.com") && lower.contains("/other/"))
    }

    suspend fun subtitles(subjectId: String, resourceId: String): List<Subtitle> {
        ensureSession()
        val path = "/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=$subjectId&resourceId=$resourceId"
        return try {
            val json = withRetry("GET", path, null)
            val caps = json.optJSONObject("extCaptions")
                ?: json.optJSONObject("data")?.optJSONObject("extCaptions")
            val out = mutableListOf<Subtitle>()
            val seen = mutableSetOf<String>()
            val items = caps?.optJSONArray("list") ?: caps?.optJSONArray("captions")
            items?.let { arr ->
                for (i in 0 until arr.length()) {
                    val c = arr.getJSONObject(i)
                    val url = c.optString("url")
                    if (url.isBlank() || url.contains("aa348f2541d13ffe")) continue
                    val size = c.optInt("size", 0)
                    if (size in 1..50) continue
                    val name = c.optString("lanName").ifBlank { c.optString("lan") }.ifBlank { "Subtitles" }
                    if (name.equals("in", true) && (size == 0 || size <= 100)) continue
                    if (seen.add(url)) out.add(Subtitle(name, url))
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---------------- parsing ----------------

    private fun parseCatalog(json: JSONObject): List<CatalogItem> {
        val data = json.optJSONObject("data") ?: json
        val list = data.optJSONArray("list") ?: data.optJSONArray("data") ?: return emptyList()
        val items = mutableListOf<CatalogItem>()
        for (i in 0 until list.length()) {
            list.getJSONObject(i).toCatalogItem()?.let { items.add(it) }
        }
        return items
    }

    private fun JSONObject.toCatalogItem(): CatalogItem? {
        val id = (opt("subjectId") ?: opt("id"))?.let {
            if (it is Number) it.toString() else it.toString()
        } ?: return null
        if (id.isBlank()) return null
        val title = (opt("title") ?: opt("name"))?.toString()?.ifBlank { "Unknown" } ?: "Unknown"
        val stype = (opt("subjectType") ?: opt("stype")) as? Number ?: 1
        val yearRaw = (opt("releaseDate") ?: opt("year") ?: opt("releaseInfo"))?.toString() ?: null
        val year = yearRaw?.let {
            val m = Regex("(19|20)\\d{2}").find(it)
            m?.value
        }
        val cover = (opt("cover") as? JSONObject)?.optString("url")
            ?: optString("coverUrl").ifBlank { optString("poster") }.ifBlank { optString("pic") }
        val backdrop = (opt("backdrop") as? JSONObject)?.optString("url") ?: optString("backdropUrl")
        val rating = opt("rating")?.toString()?.let {
            if (it.toDoubleOrNull() == null) null else it
        } ?: optString("score").ifBlank { null }
        val desc = opt("description")?.toString() ?: optString("desc").ifBlank { null }
        val genres = (opt("genres") as? JSONArray)?.let { arr ->
            (0 until arr.length()).map { arr.optString(it).ifBlank { "" } }.filter { it.isNotEmpty() }
        } ?: emptyList()
        val duration = opt("duration")?.toString() ?: null
        val seasonCount = (opt("season") as? Number)?.toInt() ?: 0
        return CatalogItem(
            id = id, title = title,
            type = if (stype == 2) "series" else "movie",
            year = year, poster = cover?.ifBlank { null },
            backdrop = backdrop?.ifBlank { null }, rating = rating,
            description = desc, genres = genres, duration = duration,
            seasonCount = seasonCount,
        )
    }

    private fun JSONObject.toMediaDetail(seasons: List<Season>): MediaDetail {
        val catalog = toCatalogItem() ?: CatalogItem("", "Unknown", "movie")
        return MediaDetail(
            id = catalog.id, title = catalog.title, type = catalog.type,
            year = catalog.year, poster = catalog.poster, backdrop = catalog.backdrop,
            rating = catalog.rating, description = catalog.description,
            genres = catalog.genres, duration = catalog.duration,
            seasons = seasons,
        )
    }

    private fun parseSeasons(data: JSONObject): List<Season> {
        val arr = data.optJSONArray("seasons") ?: data.optJSONArray("list") ?: return emptyList()
        val seasons = mutableListOf<Season>()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val seasonNo = s.optInt("season", s.optInt("se", i + 1))
            val eps = mutableListOf<Episode>()
            val epArr = s.optJSONArray("episodes") ?: s.optJSONArray("list") ?: continue
            for (j in 0 until epArr.length()) {
                val e = epArr.getJSONObject(j)
                eps.add(Episode(e.optInt("episode", e.optInt("ep", j + 1)), e.optString("title").ifBlank { null }))
            }
            seasons.add(Season(seasonNo, eps))
        }
        return seasons.sortedBy { it.number }
    }

    private fun parseReleases(json: JSONObject, season: Int, episode: Int, out: MutableMap<String, Stream>) {
        val data = json.optJSONObject("data") ?: json
        val list = data.optJSONArray("list") ?: return
        for (i in 0 until list.length()) {
            val r = list.getJSONObject(i)
            var url = r.optString("url").ifBlank { r.optString("playUrl") }
            if (url.isBlank() || isTrailerUrl(url)) continue
            val name = r.optString("name").ifBlank { r.optString("title") }.ifBlank { "Stream" }
            val quality = r.optInt("resolution", 0)
            val qStr = when {
                quality >= 2160 -> "4K"
                quality >= 1080 -> "1080p"
                quality >= 720 -> "720p"
                quality >= 480 -> "480p"
                else -> "Auto"
            }
            val sizeMb = r.optDouble("size", -1.0).takeIf { it > 0 }?.let { it / 1024.0 / 1024.0 }
            val key = url.substringBefore('?')
            if (key.isNotBlank() && !out.containsKey(key)) {
                out[key] = Stream(
                    name = name, quality = qStr, url = url, sizeMb = sizeMb,
                    headers = mapOf("User-Agent" to userAgent),
                )
            }
        }
    }

}
