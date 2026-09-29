package technology.zman

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import technology.zman.core.Lang

/**
 * Les pages du site, lues dans l'app : blocs JSON produits par
 * scripts/gen-app-texts.py à partir de web/content (assets/texts).
 */
enum class Page(val slug: String) {
    MANIFESTE("manifeste"), METHODE("methode"), API("api"), CONFIDENTIALITE("confidentialite");

    fun title(lang: Lang): String {
        val tr = Tr(lang)
        return when (this) {
            MANIFESTE -> tr("Manifeste", "Manifesto", "מניפסט")
            METHODE -> tr("Méthode", "Method", "שיטה")
            API -> tr("L’API", "The API", "ה־API")
            CONFIDENTIALITE -> tr("Confidentialité", "Privacy", "פרטיות")
        }
    }

    fun summary(lang: Lang): String {
        val tr = Tr(lang)
        return when (this) {
            MANIFESTE -> tr("Un temps de rotation, pas un temps d’atome", "Rotation time, not atomic time", "זמן של סיבוב, לא זמן של אטום")
            METHODE -> tr("Le calcul, ce qui est vérifié, ce qui reste à faire", "The computation, what is tested, what remains", "החישוב, מה נבדק ומה נותר")
            API -> tr("Le timestamp juif, pour les développeurs", "The Jewish timestamp, for developers", "חותמת הזמן היהודית, למפתחים")
            CONFIDENTIALITE -> tr("Rien n’est collecté", "Nothing is collected", "דבר אינו נאסף")
        }
    }

    fun blocks(context: Context, lang: Lang): List<Block> = runCatching {
        val json = context.assets.open("texts/${lang.name.lowercase()}-$slug.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        List(arr.length()) { Block.of(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    companion object {
        fun of(slug: String): Page? = entries.firstOrNull { it.slug == slug }
    }
}

class Block(val t: String, val s: String?, val items: List<String>, val rows: List<List<String>>) {
    companion object {
        private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else List(a.length()) { a.getString(it) }

        fun of(o: JSONObject): Block {
            val rows = o.optJSONArray("rows")
            return Block(
                o.getString("t"),
                if (o.has("s")) o.getString("s") else null,
                strings(o.optJSONArray("items")),
                if (rows == null) emptyList() else List(rows.length()) { strings(rows.getJSONArray(it)) },
            )
        }
    }
}

@Composable
fun ReaderScreen(page: Page, lang: Lang, open: (Page) -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val blocks = remember(page, lang) { page.blocks(context, lang) }
    val md = remember(p, open) { Markdown(p, open) }
    ScrollPage(maxWidth = 680.dp, horizontal = 22.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            blocks.forEach { Block(it, lang, md) }
        }
    }
}

@Composable
private fun Block(b: Block, lang: Lang, md: Markdown) {
    val p = LocalPalette.current
    when (b.t) {
        "h1" -> {
            Para(md(b.s), 32.sp, p.ink, weight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
        }
        "lede" -> {
            Para(md(b.s), 19.sp, p.ink2, italic = lang != Lang.HE)
            Spacer(Modifier.height(18.dp))
        }
        "h2" -> {
            Spacer(Modifier.height(22.dp))
            Rule()
            Spacer(Modifier.height(10.dp))
            Para(md(b.s), 23.sp, p.ink, weight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
        }
        "h3" -> {
            Spacer(Modifier.height(16.dp))
            Para(md(b.s), 18.sp, p.accent, weight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
        }
        "p" -> {
            Para(md(b.s), 17.sp, p.ink, lineHeight = 1.45f)
            Spacer(Modifier.height(14.dp))
        }
        "ul", "ol" -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                b.items.forEachIndexed { i, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Para(AnnotatedString(if (b.t == "ol") "${i + 1}." else "•"), 17.sp, p.accent, fill = false)
                        Para(md(item), 17.sp, p.ink, lineHeight = 1.4f)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        "pre" -> {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(p.paper2, RoundedCornerShape(6.dp))
                        .horizontalScroll(rememberScrollState()),
                ) {
                    BasicText(
                        b.s.orEmpty(),
                        Modifier.padding(12.dp),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = p.ink, lineHeight = 17.sp),
                        softWrap = false,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        "table" -> {
            Table(b.rows, md)
            Spacer(Modifier.height(14.dp))
        }
        "sources" -> {
            Spacer(Modifier.height(22.dp))
            Rule()
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                b.items.forEachIndexed { i, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Para(AnnotatedString("${i + 1}."), 14.sp, p.ink2, fill = false, tnum = true)
                        Para(md(item), 14.sp, p.ink)
                    }
                }
            }
        }
    }
}

/** Un tableau du site, en fiches : sur un téléphone, trois colonnes ne tiennent pas. */
@Composable
private fun Table(rows: List<List<String>>, md: Markdown) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        rows.drop(1).forEach { row ->
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Para(md(row.firstOrNull()), 16.sp, p.ink, weight = FontWeight.Medium)
                if (row.size > 1) Para(md(row[1]), 16.sp, p.accent)
                if (row.size > 2 && row[2].isNotEmpty()) Para(md(row[2]), 14.sp, p.ink2)
            }
            Rule()
        }
    }
}

/** Paragraphe aligné au début de la ligne (à droite en hébreu). */
@Composable
private fun Para(
    text: AnnotatedString,
    size: TextUnit,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    italic: Boolean = false,
    lineHeight: Float = 1.3f,
    fill: Boolean = true,
    tnum: Boolean = false,
) {
    BasicText(
        text,
        modifier = if (fill) Modifier.fillMaxWidth() else Modifier,
        style = TextStyle(
            fontFamily = Garamond, fontSize = size, color = color, fontWeight = weight,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textAlign = TextAlign.Start, lineHeight = size * lineHeight,
            fontFeatureSettings = if (tnum) "tnum, lnum" else null,
        ),
    )
}

/**
 * Markdown en ligne des blocs (*italique*, **gras**, `code`, [liens](url), \échappements)
 * → AnnotatedString, hébreu en Frank Ruhl Libre. Les liens zman://<page>
 * ouvrent la page dans l'app ; les autres, le navigateur.
 */
private class Markdown(val p: Palette, val open: (Page) -> Unit) {
    private class Run(val text: String, val bold: Boolean, val italic: Boolean, val code: Boolean, val link: Int)

    operator fun invoke(s: String?): AnnotatedString {
        val src = s.orEmpty()
        val runs = mutableListOf<Run>()
        val links = mutableListOf<String>()
        var bold = false
        var italic = false
        var link = -1
        val buf = StringBuilder()
        fun flush(code: Boolean = false) {
            if (buf.isNotEmpty()) runs += Run(buf.toString(), bold, italic, code, link)
            buf.clear()
        }
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c == '\\' && i + 1 < src.length -> { buf.append(src[i + 1]); i += 2; continue }
                c == '`' && src.indexOf('`', i + 1) > i -> {
                    flush()
                    val end = src.indexOf('`', i + 1)
                    buf.append(src, i + 1, end)
                    flush(code = true)
                    i = end + 1; continue
                }
                c == '*' && src.startsWith("**", i) -> { flush(); bold = !bold; i += 2; continue }
                c == '*' -> { flush(); italic = !italic; i += 1; continue }
                c == '[' && link < 0 && src.indexOf("](", i) > i -> {
                    flush(); link = links.size; links += ""; i += 1; continue
                }
                c == ']' && link >= 0 && src.startsWith("](", i) -> {
                    flush()
                    // URL jusqu'à la parenthèse fermante équilibrée.
                    var j = i + 2
                    var depth = 1
                    while (j < src.length) {
                        if (src[j] == '(') depth++
                        if (src[j] == ')' && --depth == 0) break
                        j++
                    }
                    links[link] = src.substring(i + 2, minOf(j, src.length))
                    link = -1
                    i = j + 1; continue
                }
            }
            buf.append(c)
            i++
        }
        flush()

        return buildAnnotatedString {
            var k = 0
            while (k < runs.size) {
                val l = runs[k].link
                if (l < 0) {
                    append(runs[k]); k++; continue
                }
                // Toutes les suites du même lien, sous une seule annotation.
                val url = links[l]
                val styles = TextLinkStyles(SpanStyle(color = p.accent, textDecoration = TextDecoration.Underline))
                val annotation = if (url.startsWith("zman://")) {
                    LinkAnnotation.Clickable(url, styles) { Page.of(url.removePrefix("zman://").trim('/'))?.let(open) }
                } else {
                    LinkAnnotation.Url(url, styles)
                }
                withLink(annotation) {
                    while (k < runs.size && runs[k].link == l) { append(runs[k]); k++ }
                }
            }
        }
    }

    private fun AnnotatedString.Builder.append(r: Run) {
        val style = SpanStyle(
            fontWeight = if (r.bold) FontWeight.SemiBold else null,
            fontStyle = if (r.italic) FontStyle.Italic else null,
            fontFamily = if (r.code) FontFamily.Monospace else null,
            fontSize = if (r.code) 0.85.em else TextUnit.Unspecified,
            background = if (r.code) p.paper2 else Color.Unspecified,
        )
        pushStyle(style)
        if (r.code) append(r.text) else append(scripted(r.text))
        pop()
    }
}
