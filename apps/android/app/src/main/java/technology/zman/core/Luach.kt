package technology.zman.core

// Portage de pkg/luach : calendrier fixe hébraïque par arithmétique pure sur
// des numéros de jours (jour 1 = lundi = 1 Tishrei de l'an 1).

/** Mois en numérotation biblique : Nissan = 1 … Adar = 12, Adar II = 13. */
data class HebrewDate(val year: Long, val month: Int, val day: Int)

object Luach {
    const val NISSAN = 1; const val IYAR = 2; const val SIVAN = 3; const val TAMMUZ = 4
    const val AV = 5; const val ELUL = 6; const val TISHREI = 7; const val CHESHVAN = 8
    const val KISLEV = 9; const val TEVET = 10; const val SHEVAT = 11; const val ADAR = 12
    const val ADAR_II = 13

    const val SUNDAY = 0; const val MONDAY = 1; const val TUESDAY = 2; const val WEDNESDAY = 3
    const val THURSDAY = 4; const val FRIDAY = 5; const val SHABBAT = 6

    private const val CHALAKIM_PER_DAY = Rega.CHALAKIM_PER_DAY
    const val LUNAR_MONTH_CHALAKIM = 29 * CHALAKIM_PER_DAY + 12 * Rega.CHALAKIM_PER_HOUR + 793 // 765 433
    const val MOLAD_TOHU_CHALAKIM = 1 * CHALAKIM_PER_DAY + 5 * Rega.CHALAKIM_PER_HOUR + 204 // 31 524

    private const val MOLAD_ZAKEN_PARTS = 18 * Rega.CHALAKIM_PER_HOUR
    private const val GATRAD_PARTS = 9 * Rega.CHALAKIM_PER_HOUR + 204
    private const val BETUTEKPAT_PARTS = 15 * Rega.CHALAKIM_PER_HOUR + 589

    fun isLeap(year: Long): Boolean = Math.floorMod(7 * year + 1, 19L) < 7

    fun monthsInYear(year: Long): Int = if (isLeap(year)) 13 else 12

    private fun monthsElapsed(year: Long): Long = Math.floorDiv(235 * year - 234, 19L)

    fun moladTishreiChalakim(year: Long): Long = MOLAD_TOHU_CHALAKIM + monthsElapsed(year) * LUNAR_MONTH_CHALAKIM

    /** Numéro du jour du 1 Tishrei, après les quatre dehiyot. */
    fun roshHashana(year: Long): Long {
        val m = moladTishreiChalakim(year)
        var day = Math.floorDiv(m, CHALAKIM_PER_DAY)
        val parts = m - day * CHALAKIM_PER_DAY
        val wd = Math.floorMod(day, 7L).toInt()
        when {
            parts >= MOLAD_ZAKEN_PARTS -> day++ // molad zaken
            wd == TUESDAY && parts >= GATRAD_PARTS && !isLeap(year) -> day++ // GaTaRaD
            wd == MONDAY && parts >= BETUTEKPAT_PARTS && isLeap(year - 1) -> day++ // BeTU'TeKPaT
        }
        when (Math.floorMod(day, 7L).toInt()) {
            SUNDAY, WEDNESDAY, FRIDAY -> day++ // לא אד״ו ראש
        }
        return day
    }

    fun yearLength(year: Long): Int = (roshHashana(year + 1) - roshHashana(year)).toInt()

    fun monthLength(year: Long, month: Int): Int = when (month) {
        NISSAN, SIVAN, AV, TISHREI, SHEVAT -> 30
        IYAR, TAMMUZ, ELUL, TEVET -> 29
        CHESHVAN -> if (yearLength(year) % 10 == 5) 30 else 29
        KISLEV -> if (yearLength(year) % 10 == 3) 29 else 30
        ADAR -> if (isLeap(year)) 30 else 29
        ADAR_II -> if (isLeap(year)) 29 else 0
        else -> 0
    }

    fun monthsInOrder(year: Long): List<Int> =
        if (isLeap(year)) listOf(TISHREI, CHESHVAN, KISLEV, TEVET, SHEVAT, ADAR, ADAR_II, NISSAN, IYAR, SIVAN, TAMMUZ, AV, ELUL)
        else listOf(TISHREI, CHESHVAN, KISLEV, TEVET, SHEVAT, ADAR, NISSAN, IYAR, SIVAN, TAMMUZ, AV, ELUL)

    fun weekday(day: Long): Int = Math.floorMod(day, 7L).toInt()

    /** Numéro du jour d'une date, ou null si la date n'existe pas. */
    fun dayOf(date: HebrewDate): Long? {
        if (date.year < 1) return null
        val months = monthsInOrder(date.year)
        val idx = months.indexOf(date.month)
        if (idx < 0 || date.day < 1 || date.day > monthLength(date.year, date.month)) return null
        var d = roshHashana(date.year)
        for (m in months.subList(0, idx)) d += monthLength(date.year, m)
        return d + date.day - 1
    }

    /** Date du jour donné, ou null avant le 1 Tishrei 1. */
    fun dateOf(day: Long): HebrewDate? {
        if (day < 1) return null
        var year = maxOf(1L, day * 19 / 6940 + 1)
        while (roshHashana(year) > day) year--
        while (roshHashana(year + 1) <= day) year++
        var rem = day - roshHashana(year)
        for (m in monthsInOrder(year)) {
            val l = monthLength(year, m).toLong()
            if (rem < l) return HebrewDate(year, m, rem.toInt() + 1)
            rem -= l
        }
        return null
    }

    /** Molad du mois, en rega'im depuis l'epoch. */
    fun molad(year: Long, month: Int): Long? {
        if (year < 1) return null
        val idx = monthsInOrder(year).indexOf(month)
        if (idx < 0) return null
        return Rega.fromChalakim(moladTishreiChalakim(year) + idx * LUNAR_MONTH_CHALAKIM)
    }
}
