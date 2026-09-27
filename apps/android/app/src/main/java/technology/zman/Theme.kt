package technology.zman

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import technology.zman.core.R

// Encre, papier, un accent : les couleurs de web/static/css/site.css.
@Immutable
data class Palette(
    val paper: Color,
    val paper2: Color,
    val ink: Color,
    val ink2: Color,
    val rule: Color,
    val accent: Color,
)

val LightPalette = Palette(
    paper = Color(0xFFF7F3EA), paper2 = Color(0xFFEFE9DC), ink = Color(0xFF1D1A15),
    ink2 = Color(0xFF5A544A), rule = Color(0xFFD8D0BF), accent = Color(0xFF8D2B1C),
)

val DarkPalette = Palette(
    paper = Color(0xFF16140F), paper2 = Color(0xFF1F1C15), ink = Color(0xFFE9E2D3),
    ink2 = Color(0xFFA89F8C), rule = Color(0xFF3A3428), accent = Color(0xFFD8785C),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun palette(): Palette = if (isSystemInDarkTheme()) DarkPalette else LightPalette

// Polices du site (sous-ensembles latin et hébreu), en police variable ; dans le module core.
@OptIn(ExperimentalTextApi::class)
private fun garamond(weight: Int) = Font(
    R.font.eb_garamond, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun frankRuhl(weight: Int) = Font(
    R.font.frank_ruhl_libre, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Garamond = FontFamily(
    garamond(400), garamond(500), garamond(600),
    Font(R.font.eb_garamond_italic, FontWeight.Normal, FontStyle.Italic),
)

val FrankRuhl = FontFamily(frankRuhl(400), frankRuhl(500), frankRuhl(600), frankRuhl(700))
