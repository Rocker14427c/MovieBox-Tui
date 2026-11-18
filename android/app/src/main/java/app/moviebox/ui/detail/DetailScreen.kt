package app.moviebox.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.moviebox.data.Stream
import app.moviebox.ui.components.ErrorState
import app.moviebox.ui.components.FullScreenLoading
import app.moviebox.ui.components.QualityChip
import app.moviebox.ui.viewmodel.MovieBoxViewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

@Composable
fun DetailScreen(
    vm: MovieBoxViewModel,
    subjectId: String,
    title: String,
    onBack: () -> Unit,
    onPlay: (Stream) -> Unit,
) {
    val state by vm.detail.collectAsState()
    val fav by remember(subjectId) { mutableStateOf(vm.isFavorite(subjectId)) }
    val detail = state.detail

    LaunchedEffect(subjectId) { vm.loadDetail(CatalogItem(subjectId, title, "movie")) }

    Box(Modifier.fillMaxSize()) {
        if (state.loading || detail == null && state.error == null) {
            FullScreenLoading()
        } else if (state.error != null) {
            ErrorState(state.error) { vm.loadDetail(CatalogItem(subjectId, title, "movie")) }
        } else if (detail != null) {
            DetailContent(
                detail = detail,
                streams = state.streams,
                streaming = state.streaming,
                fav = fav,
                onFav = {
                    vm.toggleFavorite(CatalogItem(detail.id, detail.title, detail.type, detail.year, detail.poster))
                },
                onPlay = onPlay,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun DetailContent(
    detail: app.moviebox.data.MediaDetail,
    streams: List<Stream>,
    streaming: Boolean,
    fav: Boolean,
    onFav: () -> Unit,
    onPlay: (Stream) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        // backdrop hero
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Color(0xFF1A1F33))
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(detail.backdrop ?: detail.poster)
                        .crossfade(true)
                        .build(),
                    contentDescription = detail.title,
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
                IconButton(onClick = onBack, modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                IconButton(onClick = onFav, modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)) {
                    Icon(
                        if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = Color.White,
                    )
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)) {
                    Text(detail.title, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        detail.year?.let {
                            Text("$it  ", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        detail.rating?.let {
                            Text("★ $it  ", style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFC107))
                        }
                        detail.duration?.let {
                            Text("· $it", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // meta
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                detail.genres.takeIf { it.isNotEmpty() }?.let { g ->
                    Row(Modifier.padding(bottom = 8.dp)) {
                        g.forEachIndexed { i, genre ->
                            QualityChip(genre)
                            if (i < g.lastIndex) Spacer()
                        }
                    }
                }
                detail.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // seasons
        if (detail.type == "series" && detail.seasons.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Seasons", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    detail.seasons.forEach { season ->
                        Text("Season ${season.number}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        Row(
                            modifier = Modifier.padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            season.episodes.forEach { ep ->
                                OutlinedButton(
                                    onClick = { /* TODO: episode streams */ },
                                    shape = RoundedCornerShape(10.dp),
                                ) { Text("E${ep.number}") }
                            }
                        }
                    }
                }
            }
        }

        // streams
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Available Streams", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(
                    if (streams.isEmpty()) "No streams found for this title." else
                        "${streams.size} mirror(s) available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                if (streaming) {
                    Text("Loading streams…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                streams.forEach { stream ->
                    StreamCard(stream, onPlay = { onPlay(stream) })
                }
            }
        }
        item { Box(Modifier.height(16.dp)) }
    }
}

@Composable
private fun StreamCard(stream: Stream, onPlay: () -> Unit) {
    Card(
        onClick = onPlay,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(stream.name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
                stream.sizeMb?.let {
                    Text("%.0f MB".format(it), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                QualityChip(stream.quality)
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun Spacer() { Box(Modifier.width(8.dp).height(0.dp)) }
