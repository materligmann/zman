package technology.zman

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import technology.zman.widget.ZmanWidgetReceiver
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import technology.zman.core.Calibration
import technology.zman.core.Lang
import technology.zman.core.Moment
import technology.zman.core.Names
import technology.zman.core.Rega
import technology.zman.core.ZmanClock
import kotlin.math.roundToInt

/**
 * L'horloge, comme la page d'accueil du site : date hébraïque, heure ·
 * chelek · rega, compteur absolu, qualité de la mesure. Aucune date
 * grégorienne ni heure civile.
 */
@Composable
fun ClockScreen(stateFlow: StateFlow<SyncState>, lang: Lang) {
    val p = LocalPalette.current
    val state by stateFlow.collectAsState()
    val clock = ZmanClock(state.calibration)
    // Recalcul toutes les 40 ms, comme le site.
    val tick = produceState(clock.moment(), state.calibration) {
        while (true) {
            value = clock.moment()
            delay(40)
        }
    }
    // Seules la ligne heure · chelek · rega et le compteur lisent `tick` : le
    // reste de l'écran ne se recompose qu'au changement de jour.
    val today by remember { derivedStateOf { Moment(Rega.dayStart(tick.value.day)) } }
    val direction = if (lang.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Box(
            Modifier
                .fillMaxSize()
                .background(p.paper)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DateBlock(today, lang)
                Spacer(Modifier.height(28.dp))
                TimeRow(tick, lang, ready = state.calibration != null)
                Spacer(Modifier.height(36.dp))
                Total(tick, lang)
                Spacer(Modifier.height(26.dp))
                Quality(state, lang)
                Spacer(Modifier.height(34.dp))
                WhyLink(lang)
                Spacer(Modifier.height(36.dp))
                WidgetPromo()
                Spacer(Modifier.height(40.dp))
                Note(lang)
            }
        }
    }
}

@Composable
private fun DateBlock(m: Moment, lang: Lang) {
    val p = LocalPalette.current
    val d = m.date ?: return
    ZText(Names.dateHe(d), 44.sp, p.ink, weight = FontWeight.Medium, rtl = true)
    Spacer(Modifier.height(4.dp))
    ZText(Names.dateTranslit(d, lang), 22.sp, p.ink2)
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lang != Lang.HE) {
            SmallCaps(Names.weekday(m.weekday, lang), 18.sp, p.accent, tracking = 0.1.em)
            ZText("·", 18.sp, p.accent)
        }
        ZText(Names.weekdayHe(m.weekday), 18.sp, p.accent)
    }
}

@Composable
private fun TimeRow(tick: State<Moment>, lang: Lang, ready: Boolean) {
    val m = tick.value
    val p = LocalPalette.current
    val color = if (ready) p.ink else p.rule
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Group(Names.pad(m.hour, 2), stringResource(R.string.hour), color)
            Separator()
            Group(Names.pad(m.chelek, 4), stringResource(R.string.chelek), color)
            Separator()
            Group(Names.pad(m.regaInChelek, 2), stringResource(R.string.rega), color)
        }
    }
}

private val DigitSize = 60.sp

@Composable
private fun Group(value: String, label: String, color: Color) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            value,
            style = TextStyle(fontFamily = Garamond, fontSize = DigitSize, color = color, fontFeatureSettings = "tnum, lnum"),
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        SmallCaps(label, 15.sp, p.ink2, tracking = 0.14.em)
    }
}

@Composable
private fun Separator() {
    BasicText("·", style = TextStyle(fontFamily = Garamond, fontSize = DigitSize, color = LocalPalette.current.rule))
}

@Composable
private fun Total(tick: State<Moment>, lang: Lang) {
    val m = tick.value
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            Names.group(m.rega),
            style = TextStyle(
                fontFamily = Garamond, fontWeight = FontWeight.Medium, fontSize = 19.sp, color = p.ink,
                letterSpacing = 0.04.em, fontFeatureSettings = "tnum",
            ),
        )
        Spacer(Modifier.height(3.dp))
        SmallCaps(stringResource(R.string.regaim_since_epoch), 15.sp, p.ink2, tracking = 0.1.em)
    }
}

@Composable
private fun Quality(state: SyncState, lang: Lang) {
    val p = LocalPalette.current
    val text = qualityText(state.calibration, state.offline)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (state.offline || state.calibration == null) p.ink2 else p.accent, CircleShape),
        )
        Spacer(Modifier.size(7.dp))
        ZText(text, 15.sp, p.ink2)
    }
}

@Composable
private fun qualityText(c: Calibration?, offline: Boolean): String = when {
    c == null -> stringResource(if (offline) R.string.never_synced else R.string.syncing)
    offline -> stringResource(R.string.offline)
    c.source != "iers" -> stringResource(R.string.fallback)
    else -> stringResource(
        R.string.data_age,
        c.dut1AgeDays.roundToInt(),
        stringResource(if (c.predicted) R.string.predicted else R.string.measured),
    )
}

@Composable
private fun WhyLink(lang: Lang) {
    val p = LocalPalette.current
    val uri = LocalUriHandler.current
    val url = "${technology.zman.core.ZmanApi.BASE}/${lang.name.lowercase()}/manifeste"
    ZText(
        stringResource(R.string.why), 17.sp, p.ink,
        modifier = Modifier.clickable(role = Role.Button) { uri.openUri(url) },
        decoration = TextDecoration.Underline,
    )
}

/**
 * Le widget n'est visible nulle part ailleurs : l'app le signale et, si le
 * lanceur le permet, propose de l'épingler directement.
 */
@Composable
private fun WidgetPromo() {
    val p = LocalPalette.current
    val context = LocalContext.current
    val canPin = remember { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }
    Column(Modifier.widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SmallCaps(stringResource(R.string.widget_title), 15.sp, p.accent, tracking = 0.12.em)
        Spacer(Modifier.height(8.dp))
        ZText(stringResource(R.string.widget_hint), 15.sp, p.ink2)
        Spacer(Modifier.height(14.dp))
        if (canPin) {
            ZText(
                stringResource(R.string.widget_add), 16.sp, p.accent,
                modifier = Modifier
                    .border(1.dp, p.accent, RoundedCornerShape(50))
                    .clickable(role = Role.Button) {
                        AppWidgetManager.getInstance(context).requestPinAppWidget(
                            ComponentName(context, ZmanWidgetReceiver::class.java), null, null,
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        } else {
            ZText(stringResource(R.string.widget_manual), 14.sp, p.ink2)
        }
    }
}

@Composable
private fun Note(lang: Lang) {
    val p = LocalPalette.current
    Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.rule))
        Spacer(Modifier.height(16.dp))
        ZText(stringResource(R.string.note), 15.sp, p.ink2, italic = lang != Lang.HE)
        Spacer(Modifier.height(20.dp))
        val uri = LocalUriHandler.current
        ZText(
            stringResource(R.string.privacy), 14.sp, p.ink2,
            modifier = Modifier.clickable(role = Role.Button) {
                uri.openUri("${technology.zman.core.ZmanApi.BASE}/${lang.name.lowercase()}/confidentialite")
            },
            decoration = TextDecoration.Underline,
        )
    }
}

/**
 * Texte centré où chaque suite de caractères hébreux prend Frank Ruhl Libre et
 * le reste EB Garamond : les deux polices sont des sous-ensembles, comme sur
 * le site, et Compose ne fait pas de repli d'une famille à l'autre.
 */
@Composable
fun ZText(
    text: String,
    size: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    italic: Boolean = false,
    rtl: Boolean = false,
    decoration: TextDecoration? = null,
) {
    val content = @Composable {
        BasicText(
            scripted(text),
            modifier = modifier,
            style = TextStyle(
                fontFamily = Garamond, fontSize = size, color = color, fontWeight = weight,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                textAlign = TextAlign.Center, textDecoration = decoration,
                lineHeight = size * 1.35f,
            ),
        )
    }
    if (rtl) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content) else content()
}

/** Petites capitales synthétisées (les sous-ensembles n'ont pas `smcp`) ; l'hébreu reste tel quel. */
@Composable
fun SmallCaps(text: String, size: TextUnit, color: Color, tracking: TextUnit = 0.1.em) {
    if (text.any { isHebrew(it) }) {
        ZText(text, size * 1.05f, color)
    } else {
        BasicText(
            text.uppercase(),
            style = TextStyle(
                fontFamily = Garamond, fontWeight = FontWeight.Medium, fontSize = size * 0.78f,
                color = color, letterSpacing = tracking, textAlign = TextAlign.Center,
            ),
        )
    }
}

private fun isHebrew(c: Char) = c in '֐'..'׿'

fun scripted(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val hebrew = isHebrew(text[i])
        var j = i
        // Les espaces et la ponctuation suivent la suite en cours.
        while (j < text.length && (isHebrew(text[j]) == hebrew || (!text[j].isLetterOrDigit() && j > i))) j++
        if (hebrew) {
            pushStyle(SpanStyle(fontFamily = FrankRuhl))
            append(text, i, j)
            pop()
        } else {
            append(text, i, j)
        }
        i = j
    }
}
