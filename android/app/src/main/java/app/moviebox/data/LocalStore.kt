package app.moviebox.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local-only persistence: watch history, favorites and settings. Zero telemetry. */
class LocalStore(private val dir: File) {

    init { dir.mkdirs() }

    private fun file(name: String) = File(dir, name)

    fun loadHistory(): List<ResumePoint> {
        val arr = JSONArray(file("history.json").readText())
        val out = mutableListOf<ResumePoint>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(ResumePoint(
                id = o.getString("id"), title = o.getString("title"), type = o.optString("type"),
                poster = o.optString("poster").ifBlank { null },
                positionMs = o.optLong("positionMs"), durationMs = o.optLong("durationMs"),
                season = o.optInt("season"), episode = o.optInt("episode"),
            ))
        }
        return out.sortedByDescending { it.positionMs }
    }

    fun saveHistory(items: List<ResumePoint>) {
        val arr = JSONArray()
        items.take(200).forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id); put("title", p.title); put("type", p.type)
                p.poster?.let { put("poster", it) }
                put("positionMs", p.positionMs); put("durationMs", p.durationMs)
                put("season", p.season); put("episode", p.episode)
            })
        }
        file("history.json").writeText(arr.toString())
    }

    fun updatePosition(point: ResumePoint) {
        val items = loadHistory().filterNot { it.id == point.id }.toMutableList()
        items.add(point)
        saveHistory(items)
    }

    fun loadFavorites(): List<CatalogItem> {
        val arr = JSONArray(file("favorites.json").readText())
        val out = mutableListOf<CatalogItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(CatalogItem(
                id = o.getString("id"), title = o.getString("title"), type = o.optString("type"),
                year = o.optString("year").ifBlank { null },
                poster = o.optString("poster").ifBlank { null },
                backdrop = o.optString("backdrop").ifBlank { null },
                rating = o.optString("rating").ifBlank { null },
                description = o.optString("description").ifBlank { null },
                genres = JSONArray(o.optString("genres")).let { arr -> (0 until arr.length()).map { arr.getString(it) } }.ifEmpty { emptyList() },
            ))
        }
        return out
    }

    fun setFavorite(item: CatalogItem, favorite: Boolean) {
        val items = loadFavorites().toMutableList()
        val idx = items.indexOfFirst { it.id == item.id }
        if (favorite && idx < 0) items.add(item)
        if (!favorite && idx >= 0) items.removeAt(idx)
        val arr = JSONArray()
        items.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id); put("title", c.title); put("type", c.type)
                c.year?.let { put("year", it) }
                c.poster?.let { put("poster", it) }
                c.backdrop?.let { put("backdrop", it) }
                c.rating?.let { put("rating", it) }
                c.description?.let { put("description", it) }
                put("genres", JSONArray(c.genres))
            })
        }
        file("favorites.json").writeText(arr.toString())
    }

    fun isFavorite(id: String): Boolean = loadFavorites().any { it.id == id }

    private fun settingsFile() = file("settings.json")

    fun loadSettings(): Map<String, String> {
        val obj = JSONObject(settingsFile().readText())
        return obj.keys().asSequence().associateWith { obj.getString(it) }
    }

    fun saveSettings(map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        settingsFile().writeText(obj.toString())
    }
}
