package app.moviebox.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import app.moviebox.data.MediaDetail
import app.moviebox.data.Stream
import app.moviebox.ui.components.ErrorState
import app.moviebox.ui.components.FullScreenLoading
import app.moviebox.ui.components.GlassCard
import app.moviebox.ui.components.GlassPill
import app.moviebox.ui.components.QualityChip
import app.moviebox.ui.components.RowLazy
import app.moviebox.ui.components.RowTitle
import app.moviebox.ui.theme.Palette
import app.moviebox.ui.viewmodel.MovieBoxViewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

@Composable
fun DetailScreen(
    vm: MovieBoxViewModel,
    subjectId: String,
    title: String,
    onBack: () -> Unit,
    onPlay: (Stream, Int, Int) -> Unit,
    onOpen: (CatalogItem) -> Unit,
) {
    val state by vm.detail.collectAsState()
    val fav by remember(subjectId) { mutableStateOf(vm.isFavorite(subjectId)) }
    val detail = state.detail
    val err = state.error

    LaunchedEffect(subjectId) { vm.loadDetail(CatalogItem(subjectId, title, "movie")) }

    Box(Modifier.fillMaxSize()) {
        if (state.loading || (detail == null && err == null)) {
            FullScreenLoading()
        } else if (err != null) {
            ErrorState(err) { vm.loadDetail(CatalogItem(subjectId, title, "movie")) }
        } else if (detail != null) {
            DetailContent(
                detail = detail,
                streams = state.streams,
                streaming = state.streaming,
                fav = fav,
                onFav = {
                    vm.toggleFavorite(
                        CatalogItem(detail.id, detail.title, detail.type, detail.year, detail.poster)
                    )
                },
                onPlay = onPlay,
                onBack = onBack,
                onOpen = onOpen,
                onLoadStreams = { s, e ->
                    vm.loadStreams(detail, CatalogItem(detail.id, detail.title, detail.type))
                },
            )
        }
    }
}

@Composable
private fun DetailContent(
    detail: MediaDetail,
    streams: List<Stream>,
    streaming: Boolean,
    fav: Boolean,
    onFav: () -> Unit,
    onPlay: (Stream, Int, Int) -> Unit,
    onBack: () -> Unit,
    onOpen: (CatalogItem) -> Unit,
    onLoadStreams: (Int, Int) -> Unit,
) {
    var selectedSeason by remember(detail.id) { mutableStateOf(0) }
    var selectedEpisode by remember(detail.id) { mutableStateOf(0) }

    LazyColumn(Modifier.fillMaxSize()) {
        // backdrop hero
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
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
                        .background(Brush.verticalGradient(Palette.heroGradient))
                )
                // glass circle back / fav buttons
                IconButton(onClick = onBack, modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Palette.glassFillStrong)
                    .border(0.5.dp, Palette.glassBorder, CircleShape)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                IconButton(onClick = onFav, modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Palette.glassFillStrong)
                    .border(0.5.dp, Palette.glassBorder, CircleShape)) {
                    Icon(
                        if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (fav) Palette.star else Color.White,
                    )
                }
                // glass title block
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(Palette.glassFill, RoundedCornerShape(16.dp))
                        .border(0.5.dp, Palette.glassBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(detail.title, style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)) {
                            detail.year?.let {
                                Text("$it  ", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            detail.rating?.let {
                                Text("★ $it  ", style = MaterialTheme.typography.bodySmall,
                                    color = Palette.star)
                            }
                            detail.duration?.let {
                                Text("· $it", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // meta
        item {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    detail.genres.takeIf { it.isNotEmpty() }?.let { g ->
                        Row(Modifier.padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            g.forEach { GlassPill(it) }
                        }
                    }
                    detail.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // seasons + episodes
        if (detail.type == "series" && detail.seasons.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Episodes", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    detail.seasons.forEach { season ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlassPill(
                                "Season ${season.number}",
                                strong = selectedSeason == season.number,
                            )
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    selectedSeason = season.number
                                    selectedEpisode = 0
                                    onLoadStreams(season.number, 0)
                                },
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play season",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (selectedSeason == season.number) {
                            Row(
                                modifier = Modifier.padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                season.episodes.forEach { ep ->
                                    val active = selectedEpisode == ep.number
                                    GlassCard(
                                        onClick = {
                                            selectedEpisode = ep.number
                                            onLoadStreams(season.number, ep.number)
                                        },
                                        modifier = Modifier.width(56.dp).height(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Box(
                                            Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("E${ep.number}", fontWeight = FontWeight.Bold,
                                                color = if (active) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // streams
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                RowTitle("Available Streams")
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
                    StreamCard(stream, onPlay = { onPlay(stream, selectedSeason, selectedEpisode) })
                }
            }
        }

        // more like this
        if (detail.similars.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    RowTitle("More Like This")
                    RowLazy(detail.similars.take(20), onOpen)
                }
            }
        }
        item { Box(Modifier.height(16.dp)) }
    }
}

@Composable
private fun StreamCard(stream: Stream, onPlay: () -> Unit) {
    GlassCard(onClick = onPlay, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp)) {
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
