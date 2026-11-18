package app.moviebox.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.moviebox.data.BingrApi
import app.moviebox.data.CatalogItem
import app.moviebox.data.LocalStore
import app.moviebox.data.MediaDetail
import app.moviebox.data.ResumePoint
import app.moviebox.data.Stream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class HomeState(
    val loading: Boolean = false,
    val error: String? = null,
    val hero: CatalogItem? = null,
    val trending: List<CatalogItem> = emptyList(),
    val latest: List<CatalogItem> = emptyList(),
    val movies: List<CatalogItem> = emptyList(),
    val series: List<CatalogItem> = emptyList(),
)

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val results: List<CatalogItem> = emptyList(),
)

data class DetailState(
    val loading: Boolean = false,
    val error: String? = null,
    val detail: MediaDetail? = null,
    val streams: List<Stream> = emptyList(),
    val streaming: Boolean = false,
)

class MovieBoxViewModel(app: Application) : AndroidViewModel(app) {

    private val api = BingrApi()
    val store = LocalStore(app.filesDir)

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _detail = MutableStateFlow(DetailState())
    val detail: StateFlow<DetailState> = _detail.asStateFlow()

    private val _favorites = MutableStateFlow<List<CatalogItem>>(store.loadFavorites())
    val favorites: StateFlow<List<CatalogItem>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow<List<ResumePoint>>(store.loadHistory())
    val history: StateFlow<List<ResumePoint>> = _history.asStateFlow()

    /**
     * Curated featured catalog (real trending titles from bingr.one). The home
     * screen resolves each via the details API so posters, ratings and backdrops
     * are always real and current.
     */
    private val featured = listOf(
        "movie:1368337", "movie:1003596", "movie:1300968", "movie:1400837",
        "movie:1185344", "movie:1423191", "movie:656908", "movie:1381071",
        "movie:1757174", "movie:1567605", "movie:1543469", "movie:1798383",
        "series:84773", "series:213375", "series:291507", "series:254630",
    )

    fun loadHome() {
        _home.update { it.copy(loading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val loaded = featured.mapNotNull { raw ->
                    val (type, id) = raw.split(":")
                    runCatching { api.details(id, type) }.getOrNull()?.let { d ->
                        d.toCatalogItem()
                    }
                }.filterNotNull()

                val movies = loaded.filter { it.type == "movie" }
                val series = loaded.filter { it.type == "series" }
                val trending = loaded.take(12)
                val latest = loaded.takeLast(4) + loaded.take(8)

                _home.value = HomeState(
                    loading = false,
                    hero = loaded.firstOrNull(),
                    trending = trending.distinctBy { it.id },
                    latest = latest.distinctBy { it.id },
                    movies = movies,
                    series = series,
                )
            }.onFailure {
                _home.value = HomeState(loading = false, error = it.message ?: "Failed to load")
            }
        }
    }

    fun setSearchQuery(q: String) {
        _search.update { it.copy(query = q) }
        if (q.isBlank()) {
            _search.update { it.copy(results = emptyList()) }
            return
        }
        _search.update { it.copy(loading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.search(q) }
                .onSuccess { _search.value = SearchState(query = q, loading = false, results = it) }
                .onFailure { _search.value = SearchState(query = q, loading = false, error = it.message ?: "Search failed") }
        }
    }

    fun loadDetail(item: CatalogItem) {
        _detail.update { DetailState(loading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.details(item.id, item.type) }
                .onSuccess { d ->
                    _detail.value = DetailState(loading = false, detail = d)
                    loadStreams(d, item)
                }
                .onFailure { _detail.value = DetailState(loading = false, error = it.message ?: "Details failed") }
        }
    }

    /** Loads streams for a detail, honouring the selected season/episode. */
    fun loadStreams(d: MediaDetail, item: CatalogItem, season: Int = 0, episode: Int = 0) {
        _detail.update { it.copy(streaming = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.streams(d.id, d.type, season, episode) }
                .onSuccess { streams -> _detail.update { it.copy(streaming = false, streams = streams) } }
                .onFailure { _detail.update { it.copy(streaming = false) } }
        }
    }

    fun isFavorite(id: String) = store.isFavorite(id)

    fun toggleFavorite(item: CatalogItem) {
        val fav = !isFavorite(item.id)
        store.setFavorite(item, fav)
        _favorites.update { if (fav) it + item else it.filterNot { f -> f.id == item.id } }
    }

    fun recordPosition(point: ResumePoint) {
        store.updatePosition(point)
        _history.update { it.filterNot { h -> h.id == point.id } + point }
    }

    fun clearHistory() {
        store.saveHistory(emptyList())
        _history.value = emptyList()
    }

    private fun MediaDetail.toCatalogItem(): CatalogItem = CatalogItem(
        id = id, title = title, type = type,
        year = year, poster = poster, backdrop = backdrop,
        rating = rating, description = description,
        genres = genres, duration = duration,
        seasonCount = seasons.size,
    )
}
