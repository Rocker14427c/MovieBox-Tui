package app.moviebox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.moviebox.ui.home.HomeScreen
import app.moviebox.ui.detail.DetailScreen
import app.moviebox.ui.library.LibraryScreen
import app.moviebox.ui.live.LiveScreen
import app.moviebox.ui.player.PlayerScreen
import app.moviebox.ui.search.SearchScreen
import app.moviebox.ui.theme.MovieBoxTheme
import app.moviebox.ui.viewmodel.MovieBoxViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

enum class Tab(val label: String, val icon: androidx.compose.material.icons.Icons) {
    Home(label = "Home", icon = Icons.Filled.Home),
    Search(label = "Search", icon = Icons.Filled.Search),
    Live(label = "Live", icon = Icons.Filled.Tv),
    Library(label = "Library", icon = Icons.Filled.Favorite),
}

sealed class Route {
    object Tabs : Route()
    data class Detail(val id: String, val title: String) : Route()
    data class Play(
        val title: String,
        val url: String,
        val poster: String?,
        val id: String,
        val type: String,
        val season: Int = 0,
        val episode: Int = 0,
        val durationMs: Long = 0,
        val headers: Map<String, String> = emptyMap(),
    ) : Route()
}

@Composable
fun MovieBoxApp(vm: MovieBoxViewModel = viewModel()) {
    var route by rememberSaveable { mutableStateOf<Route>(Route.Tabs) }
    var activeTab by rememberSaveable { mutableStateOf(Tab.Home) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab && route is Route.Tabs,
                        onClick = {
                            activeTab = tab
                            route = Route.Tabs
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        when (val r = route) {
            is Route.Detail -> DetailScreen(
                vm = vm,
                subjectId = r.id,
                title = r.title,
                onBack = { route = Route.Tabs },
                onPlay = { st ->
                    route = Route.Play(
                        title = r.title, url = st.url,
                        poster = vm.detail.value.detail?.poster,
                        id = r.id, type = vm.detail.value.detail?.type ?: "movie",
                        headers = st.headers,
                    )
                },
            )
            is Route.Play -> PlayerScreen(
                route = r,
                vm = vm,
                onBack = { route = Route.Detail(r.id, r.title) },
                onFinished = { route = Route.Tabs },
            )
            Route.Tabs -> when (activeTab) {
                Tab.Home -> HomeScreen(vm, onOpen = { route = Route.Detail(it.id, it.title) })
                Tab.Search -> SearchScreen(vm, onOpen = { route = Route.Detail(it.id, it.title) })
                Tab.Live -> LiveScreen(
                vm,
                onPlay = { ch ->
                    route = Route.Play(
                        title = ch.name, url = ch.url, poster = null,
                        id = ch.name, type = "live",
                    )
                },
            )
                Tab.Library -> LibraryScreen(vm, onOpen = { route = Route.Detail(it.id, it.title) })
            }
        }
        }
    }
}
