package app.moviebox.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

data class ParsedPlaylist(val channels: List<TvChannel>)

object M3UParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun loadPlaylist(url: String): List<TvChannel> {
        val text = client.newCall(
            okhttp3.Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        ).execute().use { it.body?.string().orEmpty() }
        return parse(text)
    }

    fun parse(content: String): List<TvChannel> {
        val channels = mutableListOf<TvChannel>()
        var name = ""
        var group = ""
        var url = ""
        var logo = ""

        fun flush() {
            if (url.isNotBlank()) {
                channels.add(TvChannel(name.ifBlank { "Channel" }, url, group, logo.ifBlank { null }))
            }
        }

        for (raw in content.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith("#") && !line.startsWith("#EXTINF") -> {
                    if (line.isEmpty()) flush()
                }
                line.startsWith("#EXTM3U") -> { /* header */ }
                line.startsWith("#EXTINF") -> {
                    name = Regex(",(.*)$").find(line)?.destructured?.component1()?.trim() ?: line.removePrefix("#EXTINF:")
                    val attrs = line.substringAfter(' ').split(',').firstOrNull()?.let { it.trim() } ?: ""
                    group = Regex("group-title=\"([^\"]*)\"").find(attrs)?.destructured?.component1() ?: ""
                    logo = Regex("logo=\"([^\"]*)\"").find(attrs)?.destructured?.component1() ?: ""
                }
                else -> {
                    url = line
                }
            }
        }
        flush()
        return channels
    }
}
