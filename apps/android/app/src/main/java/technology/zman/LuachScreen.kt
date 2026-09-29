package technology.zman

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import technology.zman.core.Feast
import technology.zman.core.HebrewDate
import technology.zman.core.Lang
import technology.zman.core.Luach
import technology.zman.core.Moadim
import technology.zman.core.MoladAnnouncement
import technology.zman.core.Names
import technology.zman.core.Observance
import technology.zman.core.Rega
import technology.zman.core.ZmanClock
import technology.zman.core.name

/**
 * Le luach : un mois hébreu en grille, de dimanche à Chabbat, avec les fêtes,
 * Roch Hodech et le molad du mois. Aucune date grégorienne.
 */
@Composable
fun LuachScreen(clock: ZmanClock, settings: Settings, lang: Lang) {
    val tr = Tr(lang)
    val today = Rega.day(rememberRega(clock, 60_000).value)
    var year by rememberSaveable { mutableLongStateOf(Luach.dateOf(today)?.year ?: 0L) }
    var month by rememberSaveable { mutableIntStateOf(Luach.dateOf(today)?.month ?: 0) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }

    fun go(day: Long) {
        val d = Luach.dateOf(day) ?: return
        year = d.year; month = d.month; selected = null
    }

    /** Mois précédent ou suivant, dans l'ordre de l'année (Tichri → Eloul). */
    fun step(delta: Int) {
        var y = year
        var order = Luach.monthsInOrder(y)
        var i = order.indexOf(month).coerceAtLeast(0) + delta
        if (i < 0) { y -= 1; order = Luach.monthsInOrder(y); i = order.size - 1 }
        if (i >= order.size) { y += 1; order = Luach.monthsInOrder(y); i = 0 }
        if (y < 1) return
        year = y; month = order[i]; selected = null
    }

    Column {
        TopBar(tr("Luach", "Luach", "לוח"), action = tr("Aujourd’hui", "Today", "היום") to { go(clock.moment().day) })
        if (year == 0L) return@Column
        val first = HebrewDate(year, month, 1)
        val firstDay = Luach.dayOf(first)!!
        val length = Luach.monthLength(year, month)
        val observances = Moadim.year(year, settings.israel).filter { it.end > firstDay && it.start < firstDay + length }
        ScrollPage(horizontal = 16.dp) {
            Header(first, lang, tr, ::step)
            Spacer(Modifier.height(18.dp))
            Grid(firstDay, length, today, selected, observances, lang, tr) { selected = it }
            Spacer(Modifier.height(22.dp))
            Detail(selected ?: today, today, settings, lang, tr)
            Spacer(Modifier.height(26.dp))
            MonthSummary(year, month, length, observances, lang, tr)
        }
    }
}

@Composable
private fun Header(first: HebrewDate, lang: Lang, tr: Tr, step: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(R.drawable.ic_chevron_back, tr("Mois précédent", "Previous month", "החודש הקודם")) { step(-1) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            ZText("${Names.monthHe(first)} ${Names.gematria(first.year)}", 30.sp, p.ink, weight = FontWeight.Medium, rtl = true)
            ZText("${Names.month(first, lang)} ${first.year}", 17.sp, p.ink2)
        }
        IconButton(R.drawable.ic_chevron_forward, tr("Mois suivant", "Next month", "החודש הבא")) { step(1) }
    }
}

@Composable
private fun Grid(
    firstDay: Long,
    length: Int,
    today: Long,
    selected: Long?,
    obs: List<Observance>,
    lang: Lang,
    tr: Tr,
    select: (Long) -> Unit,
) {
    val p = LocalPalette.current
    val lead = Luach.weekday(firstDay)
    val cells = lead + length
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tr.weekdayInitials.forEachIndexed { wd, s ->
                ZText(s, 13.sp, if (wd == Luach.SHABBAT) p.accent else p.ink2, modifier = Modifier.weight(1f).padding(bottom = 4.dp))
            }
        }
        for (row in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until 7) {
                    val i = row * 7 + col - lead
                    Box(Modifier.weight(1f)) {
                        if (i in 0 until length) {
                            val day = firstDay + i
                            Cell(day, i + 1, today, selected, obs.filter { it.contains(day) }, lang, select)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(day: Long, n: Int, today: Long, selected: Long?, obs: List<Observance>, lang: Lang, select: (Long) -> Unit) {
    val p = LocalPalette.current
    val isToday = day == today
    val main = obs.firstOrNull { it.feast.kind != Feast.Kind.ROSH_CHODESH } ?: obs.firstOrNull()
    val wd = Luach.weekday(day)
    val shape = RoundedCornerShape(8.dp)
    val bg = when {
        day == selected -> p.accent.copy(alpha = 0.14f)
        wd == Luach.SHABBAT -> p.paper2
        else -> Color.Transparent
    }
    val d = Luach.dateOf(day)!!
    val label = "${Names.dateTranslit(d, lang)}, ${Names.weekday(wd, lang)}" + obs.joinToString("") { ", " + it.name(lang, d.year) }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .background(bg, shape)
            .border(1.5.dp, if (isToday) p.accent else Color.Transparent, shape)
            .clickable(role = Role.Button) { select(day) }
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val ink = if (main?.feast?.kind == Feast.Kind.YOM_TOV) p.accent else p.ink
        ZText(Names.gematria(n.toLong()), 19.sp, ink, weight = if (isToday) FontWeight.SemiBold else FontWeight.Normal, rtl = true)
        ZText("$n", 11.sp, p.ink2, tnum = true)
        Spacer(Modifier.height(1.dp))
        Dot(main?.let { color(it.feast.kind) } ?: Color.Transparent, 5.dp)
    }
}

@Composable
private fun color(k: Feast.Kind): Color {
    val p = LocalPalette.current
    return when (k) {
        Feast.Kind.YOM_TOV -> p.accent
        Feast.Kind.CHOL_HAMOED, Feast.Kind.MINOR, Feast.Kind.MODERN -> p.accent.copy(alpha = 0.5f)
        Feast.Kind.FAST -> p.ink2
        Feast.Kind.ROSH_CHODESH -> p.ink
    }
}

/** Le jour choisi (aujourd'hui par défaut) : date, fêtes, omer, distance. */
@Composable
private fun Detail(day: Long, today: Long, settings: Settings, lang: Lang, tr: Tr) {
    val p = LocalPalette.current
    val d = Luach.dateOf(day) ?: return
    val wd = Luach.weekday(day)
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.paper2, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ZText(Names.dateHe(d), 24.sp, p.ink, weight = FontWeight.Medium, rtl = true)
        ZText("${Names.weekday(wd, lang)} ${Names.dateTranslit(d, lang)}", 16.sp, p.ink2)
        for (o in Moadim.on(day, settings.israel)) {
            val n = day - o.start + 1
            val name = o.name(lang, d.year)
            ZText(if (o.length > 1) "$name · ${tr("jour", "day", "יום")} $n" else name, 17.sp, p.accent, weight = FontWeight.Medium)
        }
        Moadim.omer(day)?.let { ZText(tr.omer(it), 15.sp, p.accent) }
        Spacer(Modifier.height(2.dp))
        SmallCaps(tr.days(day - today), 14.sp, p.ink2)
    }
}

/** Le molad et les fêtes du mois. */
@Composable
private fun MonthSummary(year: Long, month: Int, length: Int, observances: List<Observance>, lang: Lang, tr: Tr) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(tr("Ce mois", "This month", "החודש"))
        Spacer(Modifier.height(4.dp))
        Fact(
            tr("Longueur", "Length", "אורך"),
            if (length == 30) tr("30 jours (plein)", "30 days (full)", "30 ימים (מלא)")
            else tr("29 jours (défectif)", "29 days (defective)", "29 ימים (חסר)"),
        )
        Luach.molad(year, month)?.let { Fact(tr("Molad", "Molad", "מולד"), tr.announcement(MoladAnnouncement(it))) }
        for (o in observances) {
            Fact(o.name(lang, year), tr.join(tr.range(o), Tr.shortWeekday(Luach.weekday(o.start), lang)))
        }
    }
}
