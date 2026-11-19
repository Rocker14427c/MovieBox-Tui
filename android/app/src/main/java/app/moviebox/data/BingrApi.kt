package app.moviebox.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Native client for the Bingr.one streaming service (https://bingr.one).
 *
 * Metadata endpoints (all on https://api.bingr.one/api, NOT Cloudflare-blocked):
 *   GET  /search?q=&page=            -> { page, total_pages, results: [...] }
 *   GET  /details/{movie|tv}/{id}     -> full TMDB-backed metadata + seasons + similars
 *
 * Stream resolution: the POST /stream endpoint is fronted by Cloudflare and hard-
 * blocks non-browser clients (HTTP 403, TLS-fingerprint based — header spoofing does
 * NOT bypass it). Instead, we fetch the public watch page HTML (GET, returns 200)
 * which embeds the playable stream URL for the requested title/episode, then extract
 * that URL.
 *
 *   Movie : GET https://bingr.one/watch/movie/{id}
 *   TV    : GET https://bingr.one/watch/tv/{id}/{season}/{episode}
 *
 * The embedded URL is a Cloudflare-Workers HLS bridge wrapping a signed m3u8:
 *   https://dash-hls-bridge.<...>.workers.dev/?url=<encoded master.m3u8>&Policy=...&Signature=...&Key-Pair-Id=...
 * It is directly playable in ExoPlayer (HLS). The URL appears in the page both
 * HTML-escaped (&amp;) and JS-escaped (\u0026); both are normalized to a clean URL.
 *
 * No signing, no tokens — plain HTTP with a browser-like User-Agent.
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

    /** Fetches a raw HTML page (the watch page). Returns the body text, or throws on HTTP error. */
    private suspend fun getHtml(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", ua)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", "$site/")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            text
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
     * Resolves playable streams by fetching the Bingr.one watch page for the title
     * (or the specific TV episode) and extracting the embedded HLS bridge URL.
     *
     * The POST /stream endpoint is Cloudflare-blocked for non-browser clients, so the
     * watch page (which server-renders the playable link) is the reliable source.
     */
    suspend fun streams(id: String, type: String, season: Int = 0, episode: Int = 0): List<Stream> {
        val t = if (type == "series") "tv" else "movie"
        val watchUrl = when (t) {
            "tv" -> {
                val s = if (season > 0) season else 1
                val e = if (episode > 0) episode else 1
                "$site/watch/tv/$id/$s/$e"
            }
            else -> "$site/watch/movie/$id"
        }

        val html = try {
            getHtml(watchUrl)
        } catch (_: Exception) {
            return emptyList()
        }

        val rawUrls = extractStreamUrls(html)
        if (rawUrls.isEmpty()) return emptyList()

        return rawUrls.map { raw ->
            val url = normalizeStreamUrl(raw)
            val quality = qualityFromUrl(url)
            val headers = mapOf(
                "User-Agent" to ua,
                "Referer" to "$site/",
            )
            Stream(name = "Bingr • $quality", quality = quality, url = url, headers = headers)
        }.sortedByDescending { qualityRank(it.quality) }
    }

    // ---------------- stream URL extraction ----------------

    /** Finds every Cloudflare-Workers HLS bridge URL embedded in the watch-page HTML. */
    private fun extractStreamUrls(html: String): List<String> =
        Regex("""https://dash-hls-bridge[^"'<>\s]+""")
            .findAll(html)
            .map { it.value }
            .distinct()

    /**
     * Cleans a raw extracted URL: strips trailing escape/quote artifacts and decodes
     * the param separator, which appears HTML-escaped (&amp;) or JS-escaped (\u0026).
     */
    private fun normalizeStreamUrl(raw: String): String {
        var u = raw.trimEnd('\\', '"', '\'', '<', '>')
        // JS-escaped separator: one or more backslashes followed by u0026 -> &
        u = u.replace(Regex("""\\+u0026"""), "&")
        // HTML-escaped separator: &amp; -> &
        u = u.replace("&amp;", "&")
        return u
    }

    /** Derives a quality label from the inner m3u8 segment: dash/{id}_{s}_{e}_{q}_{codec}_{bitrate}. */
    private fun qualityFromUrl(url: String): String {
        val m = Regex("""dash[%2F/][0-9]+_[0-9]+_[0-9]+_(\d{3,4})_""").find(url)
        val q = m?.groupValues?.get(1)?.toIntOrNull() ?: return "Auto"
        return when (q) {
            in 2160..9999 -> "4K"
            in 1080..2159 -> "1080p"
            in 720..1079 -> "720p"
            in 480..719 -> "480p"
            in 360..479 -> "360p"
            else -> "Auto"
        }
    }

    // ---------------- parsing ----------------

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

    private fun formatRuntime(min: Int): String? {
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
}
