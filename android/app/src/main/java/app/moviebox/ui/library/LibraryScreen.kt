package app.moviebox.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import app.moviebox.data.CatalogItem
import app.moviebox.ui.components.PosterCard
import app.moviebox.ui.viewmodel.MovieBoxViewModel

@Composable
fun LibraryScreen(vm: MovieBoxViewModel, onOpen: (CatalogItem) -> Unit) {
    val favorites by vm.favorites.collectAsState()
    val history by vm.history.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Text("Library", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp))

        if (history.isNotEmpty()) {
            Text("Continue Watching", style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, bottom = 8.dp))
            LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                items(history) { h ->
                    ContinueCard(h.title, h.positionMs, h.durationMs) { onOpen(CatalogItem(h.id, h.title, h.type)) }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = { vm.clearHistory() }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear history")
                    Text("Clear", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        Text("Favorites", style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, bottom = 8.dp))
        if (favorites.isEmpty()) {
            Text("Tap the heart on any title to add it here.",
                color = androidx.compose.ui.graphics.Color.Gray,
                modifier = Modifier.padding(16.dp))
        } else {
            LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, bottom = 16.dp),
            ) {
                items(favorites) { item -> PosterCard(item, onClick = { onOpen(item) }) }
            }
        }
    }
}

@Composable
private fun ContinueCard(title: String, positionMs: Long, durationMs: Long, onClick: () -> Unit) {
    val pct = if (durationMs > 0) (positionMs * 100 / durationMs).coerceIn(0, 100) else 0
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(title, maxLines = 2,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFF2A3348))
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth(pct / 100f).height(4.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                        .background(app.moviebox.ui.theme.Palette.accent)
                )
            }
        }
    }
}
