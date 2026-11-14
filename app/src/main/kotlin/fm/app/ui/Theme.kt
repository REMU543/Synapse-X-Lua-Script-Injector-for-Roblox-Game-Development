package fm.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1E8E4E)
private val GreenDark = Color(0xFF0E5C30)
private val Gold = Color(0xFFF2B33D)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4CC38A), onPrimary = Color(0xFF00210F),
    secondary = Gold, onSecondary = Color(0xFF2A1800),
    background = Color(0xFF0F1513), onBackground = Color(0xFFE1E6E2),
    surface = Color(0xFF161D1A), onSurface = Color(0xFFE1E6E2),
    surfaceVariant = Color(0xFF223029), onSurfaceVariant = Color(0xFFB8C7BE),
    error = Color(0xFFFF7A6E),
)
private val LightScheme = lightColorScheme(
    primary = Green, onPrimary = Color.White,
    secondary = Color(0xFFB27A00), onSecondary = Color.White,
    background = Color(0xFFF6F8F6), onBackground = Color(0xFF17201B),
    surface = Color.White, onSurface = Color(0xFF17201B),
    surfaceVariant = Color(0xFFE3ECE6), onSurfaceVariant = Color(0xFF3E4C44),
    error = Color(0xFFB3261E),
)

object Pal {
    val pitch = Color(0xFF2E7D32)
    val pitchStripe = Color(0xFF388E3C)
    val win = Color(0xFF43A047)
    val draw = Color(0xFF9E9E9E)
    val loss = Color(0xFFE53935)
    val pitchDark = GreenDark
}

@Composable
fun FmTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = Typography(), content = content)
}
