package technology.zman

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import technology.zman.core.HebrewDate
import technology.zman.core.Lang
import technology.zman.core.Luach
import technology.zman.core.MoladAnnouncement
import technology.zman.core.Names
import technology.zman.core.Rega
import technology.zman.core.YearInfo
import technology.zman.core.ZmanClock

/**
 * Le molad : compte à rebours jusqu'au prochain, les moladot de l'année au
 * format des annonces, et la structure de l'année (keviah, cycle).
 */
@Composable
fun MoladScreen(clock: ZmanClock, lang: Lang) {
    val tr = Tr(lang)
    val now = rememberRega(clock, 100).value
    var year by rememberSaveable { mutableLongStateOf(Luach.dateOf(Rega.day(now))?.year ?: 5786) }

    Column {
        TopBar(tr("Molad", "Molad", "מולד"))
        ScrollPage {
            nextMolad(now)?.let { (y, m, r) ->
                Countdown(y, m, r, now, lang, tr)
                Spacer(Modifier.height(28.dp))
            }
            YearPicker(year, tr) { year = it }
            Spacer(Modifier.height(8.dp))
            Moladot(year, now, lang, tr)
            Spacer(Modifier.height(28.dp))
            Structure(year, lang, tr)
        }
    }
}

private fun nextMolad(now: Long): Triple<Long, Int, Long>? {
    val y = Luach.dateOf(Rega.day(now))?.year ?: return null
    for (yy in listOf(y, y + 1)) {
        for (m in Luach.monthsInOrder(yy)) {
            val r = Luach.molad(yy, m) ?: continue
            if (r > now) return Triple(yy, m, r)
        }
    }
    return null
}

@Composable
private fun Countdown(year: Long, month: Int, rega: Long, now: Long, lang: Lang, tr: Tr) {
    val p = LocalPalette.current
    val d = HebrewDate(year, month, 1)
    val left = Rega.split(rega - now)
    Card(Modifier.semantics(mergeDescendants = true) {}) {
        SmallCaps(tr("Prochain molad", "Next molad", "המולד הבא"), 15.sp, p.accent, tracking = 0.1.em)
        ZText("מולד ${Names.monthHe(d)}", 34.sp, p.ink, weight = FontWeight.Medium, rtl = true)
        if (lang != Lang.HE) {
            ZText(tr("Molad de ${Names.month(d, lang)}", "Molad of ${Names.month(d, lang)}", ""), 19.sp, p.ink2)
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Top,
            ) {
                CountUnit(left.day.toString(), tr("jours", "days", "ימים"))
                Sep()
                CountUnit(Names.pad(left.hour, 2), tr("heures", "hours", "שעות"))
                Sep()
                CountUnit(Names.pad(left.chelek, 4), tr("chalakim", "chalakim", "חלקים"))
                Sep()
                CountUnit(Names.pad(left.rega, 2), tr("rega’im", "rega’im", "רגעים"))
            }
        }
        ZText(tr.announcement(MoladAnnouncement(rega)), 16.sp, p.ink2, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun CountUnit(v: String, label: String) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(
            v,
            style = TextStyle(fontFamily = Garamond, fontSize = 30.sp, color = p.ink, fontFeatureSettings = "tnum, lnum"),
            maxLines = 1,
        )
        SmallCaps(label, 12.sp, p.ink2, tracking = 0.1.em)
    }
}

@Composable
private fun Sep() {
    BasicText("·", style = TextStyle(fontFamily = Garamond, fontSize = 30.sp, color = LocalPalette.current.rule))
}

@Composable
private fun Moladot(year: Long, now: Long, lang: Lang, tr: Tr) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(tr("Les moladot de l’année", "This year’s moladot", "מולדות השנה"))
        Spacer(Modifier.height(6.dp))
        for (m in Luach.monthsInOrder(year)) {
            val r = Luach.molad(year, m)!!
            val parts = Rega.split(r)
            val d = HebrewDate(year, m, 1)
            Column(Modifier.fillMaxWidth().alpha(if (r < now) 0.5f else 1f)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        ZText(Names.month(d, lang), 17.sp, p.ink, weight = FontWeight.Medium, align = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                        ZText(tr.announcement(MoladAnnouncement(r)), 14.sp, p.ink2, align = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.size(8.dp))
                    // Notation du calendrier : jour de la semaine, heure, chalakim.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        ZText("${Luach.weekday(parts.day) + 1} · ${parts.hour} · ${parts.chelek}", 15.sp, p.ink2, tnum = true, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Rule()
            }
        }
        Spacer(Modifier.height(8.dp))
        ZText(
            tr(
                "À droite : jour de la semaine (1 = dimanche), heure depuis 18:00 la veille, chalakim — la notation du Rambam.",
                "Right: weekday (1 = Sunday), hour since 18:00 the evening before, chalakim — the Rambam’s notation.",
                "משמאל: יום בשבוע (1 = ראשון), שעה מ־18:00 בערב שלפניו, חלקים — כדרך הרמב״ם.",
            ),
            13.sp, p.ink2, align = TextAlign.Start, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Longueur, caractère, keviah, cycle de dix-neuf ans, mois. */
@Composable
private fun Structure(year: Long, lang: Lang, tr: Tr) {
    val y = YearInfo(year)
    val kind = when (y.kind) {
        YearInfo.Kind.DEFICIENT -> tr("défective (חסרה)", "deficient (חסרה)", "חסרה")
        YearInfo.Kind.REGULAR -> tr("régulière (כסדרה)", "regular (כסדרה)", "כסדרה")
        YearInfo.Kind.COMPLETE -> tr("complète (שלמה)", "complete (שלמה)", "שלמה")
    }
    val rh = Luach.weekday(y.roshHashana)
    val pe = Luach.weekday(y.pesach)
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(tr("L’année", "The year", "השנה"))
        Spacer(Modifier.height(6.dp))
        Fact(tr("Longueur", "Length", "אורך"), "${tr.dayCount(y.length)} · $kind")
        Fact(
            tr("Type", "Type", "סוג"),
            if (y.isLeap) tr("embolismique : 13 mois, avec Adar I et Adar II", "leap: 13 months, with Adar I and Adar II", "מעוברת: 13 חודשים, אדר א׳ ואדר ב׳")
            else tr("commune : 12 mois", "common: 12 months", "פשוטה: 12 חודשים"),
        )
        Fact(
            tr("Keviah", "Keviah", "קביעות"),
            "${y.keviah} · " + tr(
                "Roch Hachana ${Names.weekday(rh, Lang.FR)}, Pessah ${Names.weekday(pe, Lang.FR)}",
                "Rosh Hashana on ${Names.weekday(rh, Lang.EN)}, Pesach on ${Names.weekday(pe, Lang.EN)}",
                "ראש השנה ב${Names.weekdayHe(rh)}, פסח ב${Names.weekdayHe(pe)}",
            ),
        )
        Fact(
            tr("Cycle de 19 ans", "19-year cycle", "מחזור קטן"),
            tr("année ${y.yearInCycle} du cycle ${y.cycle}", "year ${y.yearInCycle} of cycle ${y.cycle}", "שנה ${y.yearInCycle} במחזור ${y.cycle}"),
        )
        Fact(tr("Mois", "Months", "חודשים"), y.months.joinToString(" · ") { "${tr.month(year, it.month)} ${it.length}" })
    }
}
