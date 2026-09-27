package technology.zman.wear

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import technology.zman.core.R

// La palette sombre du site, sur fond noir (écran OLED), et ses polices.
@OptIn(ExperimentalTextApi::class)
object Wear {
    val paper = Color.Black
    val ink = Color(0xFFE9E2D3)
    val ink2 = Color(0xFFA89F8C)
    val rule = Color(0xFF3A3428)
    val accent = Color(0xFFD8785C)

    // Mêmes couleurs en ARGB, pour la tuile (ProtoLayout).
    const val INK = 0xFFE9E2D3.toInt()
    const val INK2 = 0xFFA89F8C.toInt()
    const val RULE = 0xFF3A3428.toInt()
    const val ACCENT = 0xFFD8785C.toInt()

    private fun variable(res: Int, weight: Int) = Font(
        res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    val garamond = FontFamily(variable(R.font.eb_garamond, 400), variable(R.font.eb_garamond, 500))
    val frankRuhl = FontFamily(variable(R.font.frank_ruhl_libre, 400), variable(R.font.frank_ruhl_libre, 500))
}
