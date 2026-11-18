package app.moviebox.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.moviebox.data.TvChannel
import app.moviebox.ui.components.FullScreenLoading
import app.moviebox.ui.viewmodel.MovieBoxViewModel

@Composable
fun LiveScreen(vm: MovieBoxViewModel, onPlay: (TvChannel) -> Unit) {
    val channels by vm.channels.collectAsState()
    val loading by vm.channelsLoading.collectAsState()
    var url by remember { mutableStateOf(vm.playlistUrl.value) }
    val savedUrl = vm.playlistUrl.value

    LaunchedEffect(savedUrl) { if (savedUrl.isNotBlank() && channels.isEmpty()) vm.loadPlaylist(savedUrl) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Live TV", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = { Text("Paste M3U playlist URL…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(
            onClick = { vm.loadPlaylist(url) },
            enabled = url.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(if (loading) "Loading…" else "Load Playlist") }

        if (loading) {
            FullScreenLoading()
        } else if (channels.isEmpty()) {
            Text(
                "Enter a public M3U playlist URL to stream live channels.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color.Gray,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp),
            ) {
                items(channels) { ch -> ChannelCard(ch, onClick = { onPlay(ch) }) }
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: TvChannel, onClick: () -> Unit) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column {
                Text(channel.name, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                channel.group.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.Gray)
                }
            }
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Filled.Tv, contentDescription = "Live",
            )
        }
    }
}
