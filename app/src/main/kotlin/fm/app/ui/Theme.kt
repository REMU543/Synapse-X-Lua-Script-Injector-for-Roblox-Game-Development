package fm.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Selectable themes (original palettes for this game). */
enum class ThemeChoice(val id: String, val en: String) {
    ARANG("Arang (gelap)", "Charcoal (dark)"),
    MALAM("Biru Malam (gelap)", "Midnight (dark)"),
    TERANG("Terang", "Light"),
    KONTRAS("Kontras Tinggi", "High Contrast");
    fun label(lang: fm.core.Lang) = if (lang == fm.core.Lang.ID) id else en
}

/** Extra colours used by panels and tables beyond the Material scheme. */
data class FmColors(
    val sidebar: Color, val sidebarText: Color, val sidebarSelected: Color,
    val topBar: Color, val panel: Color, val panelAlt: Color, val header: Color,
    val accent: Color, val good: Color, val warn: Color, val bad: Color,
)

val LocalFm = staticCompositionLocalOf { fmFor(ThemeChoice.ARANG) }

fun fmFor(t: ThemeChoice): FmColors = when (t) {
    ThemeChoice.ARANG -> FmColors(Color(0xFF101614), Color(0xFFB9C8C0), Color(0xFF1F8F55), Color(0xFF16201C), Color(0xFF18211D),
        Color(0xFF1D2823), Color(0xFF243229), Color(0xFF4CC38A), Color(0xFF4CC38A), Color(0xFFF2B33D), Color(0xFFEF5350))
    ThemeChoice.MALAM -> FmColors(Color(0xFF0B1220), Color(0xFFB4C2DA), Color(0xFF1E6FD0), Color(0xFF111A2C), Color(0xFF131D31),
        Color(0xFF18233A), Color(0xFF1F2C47), Color(0xFF52A8FF), Color(0xFF3DD68C), Color(0xFFFFC145), Color(0xFFFF6B6B))
    ThemeChoice.TERANG -> FmColors(Color(0xFF1C3A2C), Color(0xFFD7E6DD), Color(0xFF2E9E62), Color(0xFFFFFFFF), Color(0xFFFFFFFF),
        Color(0xFFF2F6F3), Color(0xFFE3ECE6), Color(0xFF1E8E4E), Color(0xFF2E7D32), Color(0xFFB27A00), Color(0xFFC62828))
    ThemeChoice.KONTRAS -> FmColors(Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFFFD600), Color(0xFF000000), Color(0xFF0A0A0A),
        Color(0xFF141414), Color(0xFF262626), Color(0xFFFFD600), Color(0xFF00E676), Color(0xFFFFD600), Color(0xFFFF5252))
}

private fun scheme(t: ThemeChoice): ColorScheme {
    val f = fmFor(t)
    return when (t) {
        ThemeChoice.TERANG -> lightColorScheme(primary = f.accent, onPrimary = Color.White, secondary = Color(0xFFB27A00), onSecondary = Color.White,
            background = Color(0xFFF4F7F5), onBackground = Color(0xFF17201B), surface = f.panel, onSurface = Color(0xFF17201B),
            surfaceVariant = f.header, onSurfaceVariant = Color(0xFF3E4C44), error = f.bad)
        else -> darkColorScheme(primary = f.accent, onPrimary = Color.Black, secondary = f.warn, onSecondary = Color.Black,
            background = f.sidebar, onBackground = Color(0xFFE6ECE8), surface = f.panel, onSurface = Color(0xFFE6ECE8),
            surfaceVariant = f.header, onSurfaceVariant = if (t == ThemeChoice.KONTRAS) Color.White else Color(0xFFAFC0B6), error = f.bad)
    }
}

object Pal {
    val pitch = Color(0xFF2E7D32)
    val pitchStripe = Color(0xFF388E3C)
    val win = Color(0xFF43A047)
    val draw = Color(0xFF9E9E9E)
    val loss = Color(0xFFE53935)
}

/** Dense typography for phones in landscape (short screens), closer to desktop management games. */
private fun compactTypography(): Typography {
    val b = Typography()
    fun androidx.compose.ui.text.TextStyle.sz(sp: Float, lh: Float = sp * 1.3f) = copy(fontSize = androidx.compose.ui.unit.TextUnit(sp, androidx.compose.ui.unit.TextUnitType.Sp),
        lineHeight = androidx.compose.ui.unit.TextUnit(lh, androidx.compose.ui.unit.TextUnitType.Sp))
    return b.copy(
        headlineLarge = b.headlineLarge.sz(26f), headlineMedium = b.headlineMedium.sz(22f), headlineSmall = b.headlineSmall.sz(19f),
        titleLarge = b.titleLarge.sz(17f), titleMedium = b.titleMedium.sz(14.5f), titleSmall = b.titleSmall.sz(13f),
        bodyLarge = b.bodyLarge.sz(13.5f), bodyMedium = b.bodyMedium.sz(12.5f), bodySmall = b.bodySmall.sz(11.5f),
        labelLarge = b.labelLarge.sz(12.5f), labelMedium = b.labelMedium.sz(11.5f), labelSmall = b.labelSmall.sz(10.5f),
    )
}

/** True when the screen is short (a phone in landscape). */
val LocalCompact = staticCompositionLocalOf { false }

@Composable
fun FmTheme(theme: ThemeChoice, content: @Composable () -> Unit) {
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 560
    androidx.compose.runtime.CompositionLocalProvider(LocalFm provides fmFor(theme), LocalCompact provides compact) {
        MaterialTheme(colorScheme = scheme(theme), typography = if (compact) compactTypography() else Typography(), content = content)
    }
}
