package app.moviebox.data

import kotlinx.serialization.Serializable

@Serializable
data class CatalogItem(
    val id: String,
    val title: String,
    val type: String,            // "movie" | "series"
    val year: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val rating: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val duration: String? = null,
    val seasonCount: Int = 0,
)

@Serializable
data class Episode(
    val number: Int,
    val title: String? = null,
)

@Serializable
data class Season(
    val number: Int,
    val episodes: List<Episode> = emptyList(),
)

@Serializable
data class MediaDetail(
    val id: String,
    val title: String,
    val type: String,
    val year: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val rating: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val duration: String? = null,
    val seasons: List<Season> = emptyList(),
    val similars: List<CatalogItem> = emptyList(),
)

@Serializable
data class Stream(
    val name: String,            // mirror name e.g. "adilessa 1080p"
    val quality: String,         // "1080p", "720p" ...
    val url: String,
    val sizeMb: Double? = null,
    val resourceId: String? = null,
    val headers: Map<String, String> = emptyMap(),   // CDN requires UA / Referer
)

@Serializable
data class Subtitle(
    val name: String,
    val url: String,
)

@Serializable
data class ResumePoint(
    val id: String,
    val title: String,
    val type: String,
    val poster: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val season: Int = 0,
    val episode: Int = 0,
)
