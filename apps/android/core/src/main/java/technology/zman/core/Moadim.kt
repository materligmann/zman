package technology.zman.core

// Fêtes, jeûnes et Roch Hodech d'une année, par les règles du calendrier fixe
// (reports de jeûnes compris), en numéros de jours. Aucune table : tout se
// déduit de Luach. Portage de ZmanCore/Moadim.swift.

enum class Feast {
    ROSH_HASHANA, TZOM_GEDALIA, YOM_KIPPUR, SUKKOT, CHOL_HAMOED_SUKKOT, HOSHANA_RABBA,
    SHEMINI_ATZERET, SIMCHAT_TORAH, SHEMINI_ATZERET_SIMCHAT_TORAH,
    CHANUKAH, ASARA_BE_TEVET, TU_BISHVAT, PURIM_KATAN, TAANIT_ESTHER, PURIM, SHUSHAN_PURIM,
    PESACH, CHOL_HAMOED_PESACH, SHVII_SHEL_PESACH,
    YOM_HASHOAH, YOM_HAZIKARON, YOM_HAATZMAUT, PESACH_SHENI, LAG_BAOMER, YOM_YERUSHALAYIM,
    SHAVUOT, TZOM_TAMMUZ, TISHA_BE_AV, TU_BE_AV,
    ROSH_CHODESH;

    enum class Kind { YOM_TOV, CHOL_HAMOED, FAST, MINOR, MODERN, ROSH_CHODESH }

    val kind: Kind
        get() = when (this) {
            ROSH_HASHANA, YOM_KIPPUR, SUKKOT, SHEMINI_ATZERET, SIMCHAT_TORAH, SHEMINI_ATZERET_SIMCHAT_TORAH,
            PESACH, SHVII_SHEL_PESACH, SHAVUOT -> Kind.YOM_TOV
            CHOL_HAMOED_SUKKOT, HOSHANA_RABBA, CHOL_HAMOED_PESACH -> Kind.CHOL_HAMOED
            TZOM_GEDALIA, ASARA_BE_TEVET, TAANIT_ESTHER, TZOM_TAMMUZ, TISHA_BE_AV -> Kind.FAST
            YOM_HASHOAH, YOM_HAZIKARON, YOM_HAATZMAUT, YOM_YERUSHALAYIM -> Kind.MODERN
            ROSH_CHODESH -> Kind.ROSH_CHODESH
            else -> Kind.MINOR
        }
}

/** Une fête dans le calendrier : premier jour et nombre de jours. */
data class Observance(
    val feast: Feast,
    val start: Long,
    val length: Int = 1,
    /** Mois dont c'est le Roch Hodech (pour ROSH_CHODESH seulement). */
    val month: Int? = null,
) {
    val end: Long get() = start + length
    fun contains(day: Long): Boolean = day >= start && day < end
}

object Moadim {
    /**
     * Toutes les observances de l'année (1 Tichri → 29 Eloul), dans l'ordre.
     * `israel` : un seul jour de yom tov (pas de יום טוב שני של גלויות).
     */
    fun year(year: Long, israel: Boolean): List<Observance> =
        cache.getOrPut(year to israel) { compute(year, israel) }

    /** L'horloge redemande l'année à chaque image : on garde les années déjà calculées. */
    private val cache = java.util.concurrent.ConcurrentHashMap<Pair<Long, Boolean>, List<Observance>>()

    private fun compute(year: Long, israel: Boolean): List<Observance> {
        if (year < 1) return emptyList()
        fun d(month: Int, day: Int): Long = Luach.dayOf(HebrewDate(year, month, day))!!
        fun wd(day: Long): Int = Luach.weekday(day)
        // Un jeûne qui tombe le Chabbat est repoussé au dimanche.
        fun deferred(day: Long): Long = if (wd(day) == Luach.SHABBAT) day + 1 else day
        val yt = if (israel) 1 else 2
        val leap = Luach.isLeap(year)
        val adar = if (leap) Luach.ADAR_II else Luach.ADAR
        val t = Luach.TISHREI
        val n = Luach.NISSAN

        val out = mutableListOf(
            Observance(Feast.ROSH_HASHANA, d(t, 1), 2),
            Observance(Feast.TZOM_GEDALIA, deferred(d(t, 3))),
            Observance(Feast.YOM_KIPPUR, d(t, 10)),
            Observance(Feast.SUKKOT, d(t, 15), yt),
            Observance(Feast.CHOL_HAMOED_SUKKOT, d(t, 15) + yt, 6 - yt),
            Observance(Feast.HOSHANA_RABBA, d(t, 21)),
        )
        if (israel) {
            out += Observance(Feast.SHEMINI_ATZERET_SIMCHAT_TORAH, d(t, 22))
        } else {
            out += Observance(Feast.SHEMINI_ATZERET, d(t, 22))
            out += Observance(Feast.SIMCHAT_TORAH, d(t, 23))
        }
        out += Observance(Feast.CHANUKAH, d(Luach.KISLEV, 25), 8)
        out += Observance(Feast.ASARA_BE_TEVET, d(Luach.TEVET, 10))
        out += Observance(Feast.TU_BISHVAT, d(Luach.SHEVAT, 15))
        if (leap) out += Observance(Feast.PURIM_KATAN, d(Luach.ADAR, 14))
        // Ta'anit Esther ne peut être repoussé après Pourim : avancé au jeudi.
        val esther = d(adar, 13)
        out += Observance(Feast.TAANIT_ESTHER, if (wd(esther) == Luach.SHABBAT) esther - 2 else esther)
        out += Observance(Feast.PURIM, d(adar, 14))
        out += Observance(Feast.SHUSHAN_PURIM, d(adar, 15))
        out += Observance(Feast.PESACH, d(n, 15), yt)
        out += Observance(Feast.CHOL_HAMOED_PESACH, d(n, 15) + yt, 6 - yt)
        out += Observance(Feast.SHVII_SHEL_PESACH, d(n, 21), yt)

        // Jours de l'État d'Israël, avec leurs reports autour du Chabbat.
        if (year >= 5711) {
            val shoah = d(n, 27)
            out += Observance(
                Feast.YOM_HASHOAH,
                when (wd(shoah)) {
                    Luach.FRIDAY -> shoah - 1
                    Luach.SUNDAY -> shoah + 1
                    else -> shoah
                },
            )
        }
        if (year >= 5708) {
            var atzmaut = d(Luach.IYAR, 5)
            when {
                wd(atzmaut) == Luach.FRIDAY -> atzmaut -= 1
                wd(atzmaut) == Luach.SHABBAT -> atzmaut -= 2
                wd(atzmaut) == Luach.MONDAY && year >= 5764 -> atzmaut += 1
            }
            out += Observance(Feast.YOM_HAZIKARON, atzmaut - 1)
            out += Observance(Feast.YOM_HAATZMAUT, atzmaut)
        }
        out += Observance(Feast.PESACH_SHENI, d(Luach.IYAR, 14))
        out += Observance(Feast.LAG_BAOMER, d(Luach.IYAR, 18))
        if (year >= 5728) out += Observance(Feast.YOM_YERUSHALAYIM, d(Luach.IYAR, 28))
        out += Observance(Feast.SHAVUOT, d(Luach.SIVAN, 6), yt)
        out += Observance(Feast.TZOM_TAMMUZ, deferred(d(Luach.TAMMUZ, 17)))
        out += Observance(Feast.TISHA_BE_AV, deferred(d(Luach.AV, 9)))
        out += Observance(Feast.TU_BE_AV, d(Luach.AV, 15))

        // Roch Hodech : le 30 du mois précédent quand il existe, et le 1er.
        val months = Luach.monthsInOrder(year)
        for (i in 1 until months.size) {
            val m = months[i]
            val first = d(m, 1)
            val two = Luach.monthLength(year, months[i - 1]) == 30
            out += Observance(Feast.ROSH_CHODESH, if (two) first - 1 else first, if (two) 2 else 1, m)
        }
        return out.sortedWith(compareBy({ it.start }, { it.length }))
    }

    /** Les observances qui couvrent ce jour. */
    fun on(day: Long, israel: Boolean): List<Observance> {
        val date = Luach.dateOf(day) ?: return emptyList()
        // Roch Hodech en dernier : « Hanoucca » avant « Roch Hodech Tévet ».
        val list = year(date.year, israel).filter { it.contains(day) }
        return list.filter { it.feast != Feast.ROSH_CHODESH } + list.filter { it.feast == Feast.ROSH_CHODESH }
    }

    /** La prochaine fête après ce jour (sans Roch Hodech ni hol hamoed), sur cette année et la suivante. */
    fun next(after: Long, israel: Boolean): Observance? {
        val y = Luach.dateOf(after)?.year ?: return null
        return (year(y, israel) + year(y + 1, israel)).firstOrNull {
            it.start > after && it.feast.kind != Feast.Kind.ROSH_CHODESH && it.feast.kind != Feast.Kind.CHOL_HAMOED
        }
    }

    /** Jour du compte de l'omer (1 … 49), du 16 Nissan à la veille de Chavouot. */
    fun omer(day: Long): Int? {
        val date = Luach.dateOf(day) ?: return null
        val start = Luach.dayOf(HebrewDate(date.year, Luach.NISSAN, 16)) ?: return null
        val n = day - start + 1
        return if (n in 1..49) n.toInt() else null
    }
}

/** La structure d'une année : longueur, caractère, keviah, place dans le cycle. */
class YearInfo(val year: Long) {
    val isLeap: Boolean = Luach.isLeap(year)
    val length: Int = Luach.yearLength(year)
    val roshHashana: Long = Luach.roshHashana(year)
    val pesach: Long = Luach.dayOf(HebrewDate(year, Luach.NISSAN, 15))!!

    enum class Kind { DEFICIENT, REGULAR, COMPLETE }

    /** חסרה (353/383), כסדרה (354/384), שלמה (355/385). */
    val kind: Kind
        get() = when (length % 10) {
            3 -> Kind.DEFICIENT
            4 -> Kind.REGULAR
            else -> Kind.COMPLETE
        }

    /** Cycle de 19 ans (מחזור) et rang de l'année dans le cycle. */
    val cycle: Long get() = (year - 1) / 19 + 1
    val yearInCycle: Int get() = ((year - 1) % 19).toInt() + 1

    /** La keviah, p. ex. « זחא » : jour de Roch Hachana, caractère, jour de Pessah. */
    val keviah: String
        get() {
            val letters = listOf("א", "ב", "ג", "ד", "ה", "ו", "ז")
            val k = when (kind) {
                Kind.DEFICIENT -> "ח"
                Kind.REGULAR -> "כ"
                Kind.COMPLETE -> "ש"
            }
            return letters[Luach.weekday(roshHashana)] + k + letters[Luach.weekday(pesach)]
        }

    data class Month(val month: Int, val length: Int, val firstDay: Long)

    /** Mois dans l'ordre de l'année, avec leur longueur et leur premier jour. */
    val months: List<Month>
        get() {
            var day = roshHashana
            return Luach.monthsInOrder(year).map { m ->
                val l = Luach.monthLength(year, m)
                Month(m, l, day).also { day += l }
            }
        }
}

/**
 * Le molad au format des annonces (ברכת החודש) : jour de la semaine, heure
 * d'horloge depuis minuit, minutes et chalakim (18 chalakim par minute).
 */
class MoladAnnouncement(r: Long) {
    private val p = Rega.split(r)
    /** Jour halachique du molad (le jour commence à 18:00). */
    val weekday: Int = Luach.weekday(p.day)
    /** true si le molad tombe entre 18:00 et minuit : « la veille au soir ». */
    val evening: Boolean = p.hour < 6
    val clockHour: Int = (p.hour + 18) % 24
    val minute: Int = p.chelek / 18
    val chalakim: Int = p.chelek % 18
}
