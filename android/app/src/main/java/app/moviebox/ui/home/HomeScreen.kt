package app.moviebox.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.moviebox.data.CatalogItem
import app.moviebox.ui.components.ErrorState
import app.moviebox.ui.components.FullScreenLoading
import app.moviebox.ui.components.RowLazy
import app.moviebox.ui.components.RowTitle
import app.moviebox.ui.viewmodel.MovieBoxViewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

@Composable
fun HomeScreen(vm: MovieBoxViewModel, onOpen: (CatalogItem) -> Unit) {
    val state by vm.home.collectAsState()

    LaunchedEffect(Unit) { if (state.trending.isEmpty()) vm.loadHome() }

    if (state.loading) {
        FullScreenLoading("Loading your library…")
    } else if (state.error != null && state.trending.isEmpty()) {
        ErrorState(state.error) { vm.loadHome() }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Hero(state.trending.firstOrNull(), onOpen)
            Rows(state, onOpen)
        }
    }
}

@Composable
private fun Hero(item: CatalogItem?, onOpen: (CatalogItem) -> Unit) {
    if (item == null) return
    Box(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(item.backdrop ?: item.poster)
                .crossfade(true)
                .build(),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xF20B0E14), Color(0xFF0B0E14))
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                item.title, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.year?.let {
                    Text("$it · ", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.rating?.let {
                    Text("★ $it", style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFC107))
                }
            }
            Button(
                onClick = { onOpen(item) },
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Watch", Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun Rows(state: app.moviebox.ui.viewmodel.HomeState, onOpen: (CatalogItem) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        if (state.trending.isNotEmpty()) {
            RowTitle("Trending Now"); RowLazy(state.trending.take(20), onOpen)
        }
        if (state.latest.isNotEmpty()) {
            RowTitle("Latest Releases"); RowLazy(state.latest.take(20), onOpen)
        }
        if (state.movies.isNotEmpty()) {
            RowTitle("Movies"); RowLazy(state.movies.take(20), onOpen)
        }
        if (state.series.isNotEmpty()) {
            RowTitle("TV Shows"); RowLazy(state.series.take(20), onOpen)
        }
    }
}

