package technology.zman

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import technology.zman.core.Feast
import technology.zman.core.Lang
import technology.zman.core.Luach
import technology.zman.core.Moadim
import technology.zman.core.Observance
import technology.zman.core.Rega
import technology.zman.core.ZmanClock
import technology.zman.core.name
import technology.zman.core.nameHe

/**
 * Les fêtes et les jeûnes d'une année, et le compte à rebours jusqu'à la
 * prochaine, en jours, heures et chalakim.
 */
@Composable
fun MoadimScreen(clock: ZmanClock, settings: Settings, lang: Lang) {
    val tr = Tr(lang)
    val now = rememberRega(clock, 1_000).value
    val today = Rega.day(now)
    var year by rememberSaveable { mutableLongStateOf(Luach.dateOf(today)?.year ?: 5786) }
    val list = Moadim.year(year, settings.israel).filter { it.feast != Feast.ROSH_CHODESH }

    Column {
        TopBar(tr("Fêtes", "Festivals", "מועדים"))
        ScrollPage {
            // La prochaine fête (hors Roch Hodech et hol hamoed).
            Moadim.next(today, settings.israel)?.let {
                Countdown(it, now, lang, tr)
                Spacer(Modifier.height(26.dp))
            }
            YearPicker(year, tr) { year = it }
            Spacer(Modifier.height(10.dp))
            list.forEach { FeastRow(it, year, today, lang, tr) }
        }
    }
}

@Composable
private fun Countdown(o: Observance, now: Long, lang: Lang, tr: Tr) {
    val p = LocalPalette.current
    val y = Luach.dateOf(o.start)!!.year
    Card(Modifier.semantics(mergeDescendants = true) {}) {
        SmallCaps(tr("Prochaine fête", "Next festival", "המועד הבא"), 15.sp, p.accent, tracking = 0.1.em)
        ZText(o.nameHe(y), 34.sp, p.ink, weight = FontWeight.Medium, rtl = true)
        if (lang != Lang.HE) ZText(o.name(lang, y), 20.sp, p.ink2)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            ZText(tr.span(Rega.dayStart(o.start) - now), 26.sp, p.ink, tnum = true, modifier = Modifier.padding(top = 4.dp))
        }
        ZText(
            tr.join(Tr.shortWeekday(Luach.weekday(o.start), lang), tr.range(o)),
            15.sp, p.ink2,
        )
    }
}

@Composable
private fun FeastRow(o: Observance, year: Long, today: Long, lang: Lang, tr: Tr) {
    val p = LocalPalette.current
    val past = o.end <= today
    val current = o.contains(today)
    Column(Modifier.fillMaxWidth().alpha(if (past) 0.5f else 1f).semantics(mergeDescendants = true) {}) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.padding(top = 9.dp)) { Dot(dot(o.feast.kind), 7.dp) }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                val start = Modifier.fillMaxWidth()
                ZText(
                    o.name(lang, year), 17.sp, p.ink, align = TextAlign.Start, modifier = start,
                    weight = if (o.feast.kind == Feast.Kind.YOM_TOV) FontWeight.Medium else FontWeight.Normal,
                )
                if (lang != Lang.HE) ZText(o.nameHe(year), 15.sp, p.ink2, align = TextAlign.Start, modifier = start)
                ZText(
                    tr.join(Tr.shortWeekday(Luach.weekday(o.start), lang), tr.range(o), if (o.length > 1) tr.dayCount(o.length) else ""),
                    14.sp, p.ink2, align = TextAlign.Start, modifier = start,
                )
            }
            Spacer(Modifier.size(8.dp))
            ZText(
                if (current) tr("en cours", "now", "עכשיו") else tr.days(o.start - today),
                14.sp, if (current) p.accent else p.ink2,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Rule()
    }
}

@Composable
private fun dot(k: Feast.Kind): Color {
    val p = LocalPalette.current
    return when (k) {
        Feast.Kind.YOM_TOV -> p.accent
        Feast.Kind.FAST -> p.ink2
        else -> p.accent.copy(alpha = 0.45f)
    }
}
