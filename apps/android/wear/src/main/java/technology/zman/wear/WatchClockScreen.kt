package technology.zman.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import technology.zman.core.Lang
import technology.zman.core.Moment
import technology.zman.core.Names
import technology.zman.core.Rega
import technology.zman.core.ZmanClock

/**
 * L'horloge au poignet : date hébraïque, heure · chelek · rega, qualité de la
 * mesure. Aucune heure civile (pas de TimeText).
 */
@Composable
fun WatchClockScreen(stateFlow: StateFlow<SyncState>, lang: Lang) {
    val state by stateFlow.collectAsState()
    val clock = ZmanClock(state.calibration)
    val tick = produceState(clock.moment(), state.calibration) {
        while (true) {
            value = clock.moment()
            delay(40)
        }
    }
    val today by remember { derivedStateOf { Moment(Rega.dayStart(tick.value.day)) } }
    val direction = if (lang.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val context = LocalContext.current

    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    // Le Scaffold affiche la barre de défilement quand on fait défiler (au doigt ou à la couronne).
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Scaffold(
            modifier = Modifier.background(Wear.paper),
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        ) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize().background(Wear.paper),
                state = listState,
                // La date en haut à l'ouverture, pas centrée : tout tient sur un grand écran rond.
                autoCentering = null,
                contentPadding = PaddingValues(top = 34.dp, bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item { DateBlock(today, lang) }
                item { TimeRow(tick, context.getString(R.string.hour), context.getString(R.string.chelek), context.getString(R.string.rega), state.calibration != null) }
                item { Quality(context.quality(state.calibration, state.offline), ok = state.calibration != null && !state.offline) }
            }
        }
    }
}

@Composable
private fun DateBlock(m: Moment, lang: Lang) {
    val d = m.date ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        WText(Names.dayMonthHe(d), 24.sp, Wear.ink, weight = FontWeight.Medium)
        WText(Names.gematria(d.year), 15.sp, Wear.ink2)
        if (lang == Lang.HE) {
            WText(Names.weekdayHe(m.weekday), 14.sp, Wear.accent)
        } else {
            WText(Names.dateTranslit(d, lang), 14.sp, Wear.ink2)
            Spacer(Modifier.height(2.dp))
            WSmallCaps(Names.weekday(m.weekday, lang), 14.sp, Wear.accent)
        }
    }
}

@Composable
private fun TimeRow(tick: State<Moment>, hour: String, chelek: String, rega: String, ready: Boolean) {
    val m = tick.value
    val color = if (ready) Wear.ink else Wear.rule
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier.padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Group(Names.pad(m.hour, 2), hour, color)
            Dot()
            Group(Names.pad(m.chelek, 4), chelek, color)
            Dot()
            Group(Names.pad(m.regaInChelek, 2), rega, color)
        }
    }
}

@Composable
private fun Group(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(value, style = TextStyle(fontFamily = Wear.garamond, fontSize = 28.sp, color = color, fontFeatureSettings = "tnum"))
        WSmallCaps(label, 10.sp, Wear.ink2)
    }
}

@Composable
private fun Dot() {
    BasicText("·", style = TextStyle(fontFamily = Wear.garamond, fontSize = 28.sp, color = Wear.rule))
}

@Composable
private fun Quality(text: String, ok: Boolean) {
    // Le point dans le texte, pour qu'il suive la première ligne une fois centré.
    BasicText(
        buildAnnotatedString {
            pushStyle(SpanStyle(color = if (ok) Wear.accent else Wear.ink2))
            append("● ")
            pop()
            append(scripted(text))
        },
        modifier = Modifier.padding(horizontal = 22.dp),
        style = TextStyle(fontFamily = Wear.garamond, fontSize = 12.sp, color = Wear.ink2, textAlign = TextAlign.Center, lineHeight = 15.sp),
    )
}

/** Texte où les suites hébraïques prennent Frank Ruhl Libre, le reste EB Garamond. */
@Composable
private fun WText(text: String, size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal) {
    BasicText(
        scripted(text),
        style = TextStyle(
            fontFamily = Wear.garamond, fontSize = size, color = color, fontWeight = weight,
            textAlign = TextAlign.Center, lineHeight = size * 1.3f,
        ),
    )
}

/** Petites capitales synthétisées ; l'hébreu reste tel quel. */
@Composable
private fun WSmallCaps(text: String, size: TextUnit, color: Color) {
    if (text.any(::isHebrew)) {
        WText(text, size * 1.05f, color)
    } else {
        BasicText(
            text.uppercase(),
            style = TextStyle(
                fontFamily = Wear.garamond, fontWeight = FontWeight.Medium, fontSize = size * 0.78f,
                color = color, letterSpacing = 0.1.em, textAlign = TextAlign.Center,
            ),
        )
    }
}

private fun isHebrew(c: Char) = c in '֐'..'׿'

private fun scripted(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val hebrew = isHebrew(text[i])
        var j = i
        while (j < text.length && (isHebrew(text[j]) == hebrew || (!text[j].isLetterOrDigit() && j > i))) j++
        if (hebrew) {
            pushStyle(SpanStyle(fontFamily = Wear.frankRuhl))
            append(text, i, j)
            pop()
        } else {
            append(text, i, j)
        }
        i = j
    }
}
