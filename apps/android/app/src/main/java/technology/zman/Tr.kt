package technology.zman

import technology.zman.core.HebrewDate
import technology.zman.core.Lang
import technology.zman.core.Luach
import technology.zman.core.MoladAnnouncement
import technology.zman.core.Names
import technology.zman.core.Observance
import technology.zman.core.Rega

/**
 * Textes de l'interface des onglets, écrits sur place dans les trois
 * langues : `tr("Fêtes", "Festivals", "מועדים")`. Portage de Tr.swift.
 */
class Tr(val lang: Lang) {
    operator fun invoke(fr: String, en: String, he: String): String = when (lang) {
        Lang.FR -> fr
        Lang.EN -> en
        Lang.HE -> he
    }

    /** Distance en jours halachiques : « aujourd'hui », « demain », « dans 12 jours », « il y a 3 jours ». */
    fun days(n: Long): String = when {
        n == 0L -> this("aujourd’hui", "today", "היום")
        n == 1L -> this("demain", "tomorrow", "מחר")
        n == -1L -> this("hier", "yesterday", "אתמול")
        n >= 2 -> this("dans $n jours", "in $n days", "בעוד $n ימים")
        else -> this("il y a ${-n} jours", "${-n} days ago", "לפני ${-n} ימים")
    }

    /** Durée en jours, heures, chalakim : « 3 j 14 h 512 ch ». */
    fun span(regaim: Long): String {
        val r = maxOf(0L, regaim)
        val d = r / Rega.PER_DAY
        val h = (r % Rega.PER_DAY) / Rega.PER_HOUR
        val ch = (r % Rega.PER_HOUR) / Rega.PER_CHELEK
        if (lang == Lang.HE) return "\u200F$d ימ׳ \u200F$h שע׳ \u200F$ch ח׳"
        return "$d ${this("j", "d", "ימ׳")} $h ${this("h", "h", "שע׳")} $ch ${this("ch", "ch", "ח׳")}"
    }

    /** Séparateur « · », encadré de marques RLM en hébreu : sans elles, les chiffres voisins font glisser le point. */
    val dot: String get() = if (lang == Lang.HE) "\u200F · \u200F" else " · "

    /** Éléments joints par le séparateur. */
    fun join(vararg parts: String): String =
        parts.filter { it.isNotEmpty() }.joinToString(dot) { if (lang == Lang.HE) "\u200F$it" else it }

    /** Nombre de jours d'une durée : « 1 jour », « 8 jours ». */
    fun dayCount(n: Int): String =
        if (n == 1) this("1 jour", "1 day", "יום אחד") else this("$n jours", "$n days", "$n ימים")

    /** Le molad au format des annonces : « lundi 12:10 et 7 chalakim », « vendredi soir 20:59 et 1 chelek ». */
    fun announcement(a: MoladAnnouncement): String {
        val time = "${a.clockHour}:${Names.pad(a.minute, 2)}"
        val ch = when (lang) {
            Lang.FR, Lang.EN -> if (a.chalakim == 1) "1 chelek" else "${a.chalakim} chalakim"
            Lang.HE -> if (a.chalakim == 1) "חלק אחד" else "${a.chalakim} חלקים"
        }
        if (lang == Lang.HE) {
            val day = if (a.evening) "ליל " + SHORT_HE[a.weekday] else Names.weekdayHe(a.weekday)
            return "$day, $time ו־$ch"
        }
        val eve = (a.weekday + 6) % 7
        val day = if (a.evening) {
            this("${Names.weekday(eve, Lang.FR)} soir", "${Names.weekday(eve, Lang.EN)} evening", "")
        } else {
            Names.weekday(a.weekday, lang)
        }
        return "$day $time ${this("et", "and", "")} $ch"
    }

    /** En-têtes de colonnes du calendrier. */
    val weekdayInitials: List<String>
        get() = this(
            "dim.|lun.|mar.|mer.|jeu.|ven.|chab.",
            "Sun|Mon|Tue|Wed|Thu|Fri|Shab",
            "א׳|ב׳|ג׳|ד׳|ה׳|ו׳|ש׳",
        ).split("|")

    /** « 15 Nissan » ou, sur plusieurs jours, « 15 – 22 Nissan » / « 25 Kislev – 2 Tévet ». */
    fun range(o: Observance): String {
        if (lang == Lang.HE) return rangeHe(o)
        val a = Luach.dateOf(o.start)!!
        val b = Luach.dateOf(o.end - 1)!!
        if (o.length == 1) return "${a.day} ${Names.month(a, lang)}"
        if (a.month == b.month) return "${a.day} – ${b.day} ${Names.month(a, lang)}"
        return "${a.day} ${Names.month(a, lang)} – ${b.day} ${Names.month(b, lang)}"
    }

    fun rangeHe(o: Observance): String {
        val a = Luach.dateOf(o.start)!!
        val b = Luach.dateOf(o.end - 1)!!
        if (o.length == 1) return Names.dayMonthHe(a)
        if (a.month == b.month) return "${Names.gematria(a.day.toLong())}–${Names.gematria(b.day.toLong())} ${Names.monthHe(a)}"
        return "${Names.dayMonthHe(a)} – ${Names.dayMonthHe(b)}"
    }

    fun omer(n: Int): String = this("Omer : ${n}ᵉ jour", "Omer: day $n", "ספירת העומר: יום $n")

    /** Nom du mois d'une année donnée (Adar I dans une année embolismique). */
    fun month(year: Long, month: Int): String = Names.month(HebrewDate(year, month, 1), lang)

    companion object {
        val SHORT_HE = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")

        fun shortWeekday(wd: Int, lang: Lang): String =
            if (lang == Lang.HE) Names.weekdayHe(wd) else Names.weekday(wd, lang)
    }
}
