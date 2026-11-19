package app.moviebox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.moviebox.ui.home.HomeScreen
import app.moviebox.ui.detail.DetailScreen
import app.moviebox.ui.library.LibraryScreen
import app.moviebox.ui.player.PlayerScreen
import app.moviebox.ui.search.SearchScreen
import app.moviebox.ui.theme.MovieBoxTheme
import app.moviebox.ui.theme.Palette
import app.moviebox.ui.viewmodel.MovieBoxViewModel
enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home(label = "Home", icon = Icons.Filled.Home),
    Search(label = "Search", icon = Icons.Filled.Search),
    Library(label = "Library", icon = Icons.Filled.Favorite),
}

sealed class Route {
    object Tabs : Route()
    data class Detail(val id: String, val title: String, val type: String) : Route()
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
fun MovieBoxApp(vm: MovieBoxViewModel) {
    var route by remember { mutableStateOf<Route>(Route.Tabs) }
    var activeTab by remember { mutableStateOf(Tab.Home) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // Frosted-glass bottom navigation
            NavigationBar(
                containerColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.glassFill)
                    .border(0.5.dp, Palette.glassBorder, RoundedCornerShape(0.dp))
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            ) {
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
                    type = r.type,
                    onBack = { route = Route.Tabs },
                    onOpen = { route = Route.Detail(it.id, it.title, it.type) },
                    onPlay = { st, season, episode ->
                        route = Route.Play(
                            title = r.title, url = st.url,
                            poster = vm.detail.value.detail?.poster,
                            id = r.id, type = vm.detail.value.detail?.type ?: "movie",
                            season = season, episode = episode,
                            headers = st.headers,
                        )
                    },
                )
                is Route.Play -> PlayerScreen(
                    route = r,
                    vm = vm,
                    onBack = { route = Route.Detail(r.id, r.title, r.type) },
                    onFinished = { route = Route.Tabs },
                )
                Route.Tabs -> when (activeTab) {
                    Tab.Home -> HomeScreen(vm, onOpen = { route = Route.Detail(it.id, it.title, it.type) })
                    Tab.Search -> SearchScreen(vm, onOpen = { route = Route.Detail(it.id, it.title, it.type) })
                    Tab.Library -> LibraryScreen(vm, onOpen = { route = Route.Detail(it.id, it.title, it.type) })
                }
            }
        }
    }
}
