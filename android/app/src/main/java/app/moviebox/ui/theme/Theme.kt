package app.moviebox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B6CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2350),
    onPrimaryContainer = Color(0xFFDED8FF),
    secondary = Color(0xFFB59CFF),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFEDEFF7),
    surface = Color(0xFF131826),
    onSurface = Color(0xFFEDEFF7),
    surfaceVariant = Color(0xFF1E2537),
    onSurfaceVariant = Color(0xFFA7B0C8),
    outline = Color(0xFF2A3348),
    error = Color(0xFFFF6B81),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A48FF),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF141821),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141821),
)

@Composable
fun MovieBoxTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

object Palette {
    val heroGradient = listOf(Color(0xFF1A1F33), Color(0xFF0B0E14))
    val accent = Color(0xFF8B6CFF)
}
