package app.moviebox.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * Native client for the Bingr.one streaming API (https://bingr.one).
 *
 * Endpoints (all on https://api.bingr.one/api):
 *   GET  /search?q=&page=            -> { page, total_pages, results: [...] }
 *   GET  /details/{movie|tv}/{id}     -> full TMDB-backed metadata + seasons + similars
 *   POST /stream   body {srv,t,id,query} -> { scraperName, sources:[{url,quality,language,headers}] }
 *
 * Sources are direct MP4 / HLS (m3u8) links, both playable in ExoPlayer.
 * No signing, no tokens — plain JSON over HTTP with a browser-like User-Agent.
 */
class BingrApi {

    private val apiBase = "https://api.bingr.one/api"
    private val site = "https://bingr.one"
    private val ua =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Active Bingr servers — priority order matches the site's own cascade. */
    private val servers = listOf(
        Server("s40", "Aphelion"),
        Server("s70", "Polaris"),
        Server("s62", "Bastion"),
        Server("s63", "Hallyu"),
        Server("s30", "Nova"),
        Server("s60", "Vertex"),
        Server("s61", "Corvus"),
        Server("s31", "Orion"),
        Server("s3", "Edmunds"),
    )

    private data class Server(val id: String, val name: String)

    private fun baseHeaders(): Map<String, String> = mapOf(
        "User-Agent" to ua,
        "Origin" to site,
        "Referer" to "$site/",
    )

    // ---------------- low-level HTTP ----------------

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).headers(baseHeaders().toHeaders()).get().build())
            .execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}: $text")
                JSONObject(text)
            }
    }

    private suspend fun post(url: String, body: String): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .headers(baseHeaders().toHeaders())
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}: $text")
            JSONObject(text)
        }
    }

    private fun Map<String, String>.toHeaders() = okhttp3.Headers.Builder().apply {
        this@toHeaders.forEach { (k, v) -> add(k, v) }
    }.build()

    // ---------------- public endpoints ----------------

    suspend fun search(query: String, page: Int = 1): List<CatalogItem> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val json = get("$apiBase/search?q=$enc&page=$page")
        val arr = json.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toCatalogItem() }
    }

    suspend fun details(id: String, type: String): MediaDetail {
        val t = if (type == "series") "tv" else "movie"
        val json = get("$apiBase/details/$t/$id")
        return parseDetail(json, type)
    }

    /**
     * Resolves playable streams by querying every Bingr server in parallel and
     * merging the results (highest quality first, deduped, capped at MAX_STREAMS).
     */
    suspend fun streams(id: String, type: String, season: Int = 0, episode: Int = 0): List<Stream> {
        val t = if (type == "series") "tv" else "movie"

        // Fetch title/year to include in the stream query (helps server matching).
        val meta = try { details(id, type) } catch (_: Exception) { null }

        val results = servers.map { srv ->
            async(Dispatchers.IO) { requestServer(srv, id, t, season, episode, meta) }
        }.awaitAll()

        val merged = LinkedHashMap<String, Stream>()
        for (res in results) {
            for (s in res) {
                val key = s.url.substringBefore('?')
                if (key.isNotBlank()) merged.putIfAbsent(key, s)
            }
        }
        return merged.values
            .sortedByDescending { qualityRank(it.quality) }
            .take(MAX_STREAMS)
    }

    private suspend fun requestServer(
        srv: Server, id: String, t: String, season: Int, episode: Int, meta: MediaDetail?,
    ): List<Stream> = try {
        val query = JSONObject()
        meta?.title?.let { query.put("title", it) }
        meta?.year?.let { query.put("year", it) }
        if (t == "tv" && season > 0 && episode > 0) {
            query.put("season", season)
            query.put("episode", episode)
        }
        val body = JSONObject().apply {
            put("srv", srv.id)
            put("t", t)
            put("id", id)
            put("query", query)
        }
        val json = post("$apiBase/stream", body.toString())
        val sources = json.optJSONArray("sources") ?: return emptyList()
        (0 until sources.length()).mapNotNull { sources.optJSONObject(it)?.toStream(srv.name) }
            .filter { it.url.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }

    // ---------------- parsing ----------------

    private fun JSONObject.toStream(serverName: String): Stream? {
        val url = optString("url").trim()
        if (url.isBlank()) return null
        val quality = optString("quality").trim().ifBlank { "Auto" }
        val language = optString("language").trim()
        val name = if (language.isNotBlank() && language != "Original")
            "$serverName • $quality • $language" else "$serverName • $quality"
        val headers = LinkedHashMap<String, String>()
        headers["User-Agent"] = ua
        headers["Referer"] = "$site/"
        optJSONObject("headers")?.let { h ->
            h.keys().forEach { headers[it] = h.getString(it) }
        }
        return Stream(name = name, quality = quality, url = url, headers = headers)
    }

    private fun JSONObject.toCatalogItem(): CatalogItem? {
        val id = opt("id")?.toString()?.ifBlank { null } ?: return null
        val type = if (optString("type") == "tv") "series" else "movie"
        val title = optString("title").ifBlank { "Unknown" }
        return CatalogItem(
            id = id, title = title, type = type,
            year = optString("year").ifBlank { null },
            poster = optString("poster").ifBlank { null },
            backdrop = optString("backdrop").ifBlank { null },
            rating = opt("rating")?.let { if (it is Number) it.toString() else it.toString() },
            description = optString("overview").ifBlank { null },
        )
    }

    private fun parseDetail(json: JSONObject, type: String): MediaDetail {
        val id = json.opt("id")?.toString() ?: ""
        val title = json.optString("title").ifBlank { "Unknown" }
        val catalogType = if (type == "series") "series" else "movie"
        val runtime = json.opt("runtime") as? Number
        val duration = runtime?.let { formatRuntime(it.toInt()) }

        val genres = json.optJSONArray("genres")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        } ?: emptyList()

        val seasons = json.optJSONArray("seasons")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val s = arr.optJSONObject(i) ?: return@mapNotNull null
                val num = s.optInt("season", i + 1)
                val count = s.optInt("episodes", 0)
                val eps = (1..count.coerceAtLeast(0)).map { Episode(it, null) }
                Season(number = num, episodes = eps)
            }.sortedBy { it.number }
        } ?: emptyList()

        val similars = json.optJSONArray("similars")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toCatalogItem() }
        } ?: emptyList()

        return MediaDetail(
            id = id, title = title, type = catalogType,
            year = json.optString("year").ifBlank { null },
            poster = json.optString("poster").ifBlank { null },
            backdrop = json.optString("backdrop").ifBlank {
                json.optString("backdrop_original")
            }.ifBlank { null },
            rating = json.opt("rating")?.let { if (it is Number) it.toString() else it.toString() },
            description = json.optString("overview").ifBlank { null },
            genres = genres, duration = duration, seasons = seasons, similars = similars,
        )
    }

    private fun formatRuntime(min: Int): String {
        if (min <= 0) return null
        val h = min / 60
        val m = min % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    private fun qualityRank(q: String): Int {
        val s = q.lowercase()
        return when {
            s == "4k" || s.contains("2160") -> 100
            s.contains("1080") -> 90
            s.contains("720") -> 80
            s.contains("540") -> 70
            s.contains("480") -> 60
            s.contains("360") -> 50
            s == "auto" -> 40
            else -> 30
        }
    }

    private companion object {
        const val MAX_STREAMS = 12
    }
}
