package app.moviebox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C5CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF241B4D),
    onPrimaryContainer = Color(0xFFDED6FF),
    secondary = Color(0xFFA99CFF),
    background = Color(0xFF070910),
    onBackground = Color(0xFFEDF0FA),
    surface = Color(0xFF0E1120),
    onSurface = Color(0xFFEDF0FA),
    surfaceVariant = Color(0xFF161B2E),
    onSurfaceVariant = Color(0xFF9AA3C2),
    outline = Color(0xFF232B45),
    outlineVariant = Color(0xFF1B2238),
    error = Color(0xFFFF6B81),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A48FF),
    background = Color(0xFFF4F6FC),
    onBackground = Color(0xFF141821),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141821),
    surfaceVariant = Color(0xFFE7EAF5),
    onSurfaceVariant = Color(0xFF4A526B),
    outline = Color(0xFFC7CCD9),
    outlineVariant = Color(0xFFD8DCEA),
)

@Composable
fun MovieBoxTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // Streaming apps are always dark — glass reads best on deep backgrounds.
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}

object Palette {
    val heroGradient = listOf(Color(0x33000000), Color(0x88070910), Color(0xFF070910))
    val accent = Color(0xFF7C5CFF)
    val star = Color(0xFFFFC107)

    // Frosted-glass fills — translucent so content behind shows through.
    val glassFill = Color(0x26FFFFFF)   // ~15% white
    val glassFillStrong = Color(0x38FFFFFF) // ~22% white
    val glassBorder = Color(0x4DFFFFFF)  // ~30% white hairline
    val glassFillDark = Color(0x1F0E1120)
}
