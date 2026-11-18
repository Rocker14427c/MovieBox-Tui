package app.moviebox.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView
import app.moviebox.ui.Route

@Composable
fun PlayerScreen(
    route: Route.Play,
    vm: app.moviebox.ui.viewmodel.MovieBoxViewModel,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(route.headers) {
        val factory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            // The MovieBox CDNs reject requests without the proper User-Agent
            // and (for the H5 mirrors) the official-web Referer.
        if (route.headers.isNotEmpty()) factory.setDefaultRequestProperties(route.headers)
        ExoPlayer.Builder(context).setHttpDataSourceFactory(factory).build()
    }

    var captionLang by remember { mutableStateOf<String?>(null) }

    DisposableEffect(route.url) {
        player.setMediaItem(MediaItem.fromUri(route.url))
        player.prepare()
        player.playWhenReady = true
        val posListener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_ENDED) onFinished()
            }
        }
        player.addListener(posListener)
        onDispose {
            player.removeListener(posListener)
            if (player.currentPosition > 5_000) {
                vm.recordPosition(
                    app.moviebox.data.ResumePoint(
                        id = route.id, title = route.title, type = route.type,
                        poster = route.poster, positionMs = player.currentPosition,
                        durationMs = player.duration, season = route.season, episode = route.episode,
                    )
                )
            }
            player.release()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = player
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(CircleShape)
                .padding(4.dp),
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        captionLang?.let {
            Text(
                "Subtitles: $it",
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .padding(horizontal = 16.dp),
            )
        }
    }
}
