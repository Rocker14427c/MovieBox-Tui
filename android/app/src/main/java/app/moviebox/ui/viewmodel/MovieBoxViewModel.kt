package app.moviebox.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.moviebox.data.CatalogItem
import app.moviebox.data.LocalStore
import app.moviebox.data.MediaDetail
import app.moviebox.data.M3UParser
import app.moviebox.data.MovieBoxApi
import app.moviebox.data.ResumePoint
import app.moviebox.data.Stream
import app.moviebox.data.TvChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class HomeState(
    val loading: Boolean = false,
    val error: String? = null,
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

    private val api = MovieBoxApi()
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

    private val _channels = MutableStateFlow<List<TvChannel>>(emptyList())
    val channels: StateFlow<List<TvChannel>> = _channels.asStateFlow()
    private val _channelsLoading = MutableStateFlow(false)
    val channelsLoading: StateFlow<Boolean> = _channelsLoading.asStateFlow()

    private val _playlistUrl = MutableStateFlow(store.loadSettings()["playlist_url"] ?: "")
    val playlistUrl: StateFlow<String> = _playlistUrl.asStateFlow()

    fun loadHome() {
        _home.update { it.copy(loading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val trending = api.homepage("hot_movie", 1) + api.homepage("hot_tv", 1)
                val latest = api.homepage("new_movie", 1) + api.homepage("new_tv", 1)
                val movies = api.homepage("movie", 1)
                val series = api.homepage("tv", 1)
                _home.value = HomeState(
                    loading = false, trending = trending.distinctBy { it.id },
                    latest = latest.distinctBy { it.id },
                    movies = movies, series = series,
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
            runCatching { api.details(item.id) }
                .onSuccess { d ->
                    _detail.value = DetailState(loading = false, detail = d)
                    loadStreams(d)
                }
                .onFailure { _detail.value = DetailState(loading = false, error = it.message ?: "Details failed") }
        }
    }

    private fun loadStreams(d: MediaDetail) {
        _detail.update { it.copy(streaming = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.streams(d.id) }
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

    fun loadPlaylist(url: String) {
        _channelsLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val channels = M3UParser.loadPlaylist(url)
                _channels.value = channels
                store.saveSettings(store.loadSettings() + mapOf("playlist_url" to url))
            }.onFailure {
                _channels.value = emptyList()
            }.onFinally { _channelsLoading.value = false }
        }
    }

    fun savedPlaylistUrl(): String = _playlistUrl.value
}
