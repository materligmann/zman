package technology.zman.core

// Noms et nombres, repris de web/i18n/*.json et de web/static/js/clock.js.

enum class Lang {
    FR, EN, HE;

    val isRtl: Boolean get() = this == HE

    companion object {
        /** Code de langue → Lang ; le français par défaut, comme le site. */
        fun of(code: String): Lang = when (code.take(2).lowercase()) {
            "he", "iw" -> HE
            "en" -> EN
            else -> FR
        }
    }
}

object Names {
    private val ONES = arrayOf("", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט")
    private val TENS = arrayOf("", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ")
    private val HUNDREDS = arrayOf("", "ק", "ר", "ש", "ת", "תק", "תר", "תש", "תת", "תתק")

    /** Nombre hébraïque avec gershayim (jours, années). */
    fun gematria(value: Long): String {
        var n = value.toInt()
        val thousands = n / 1000
        n %= 1000
        var s = HUNDREDS[n / 100]
        n %= 100
        s += when (n) {
            15 -> "טו"
            16 -> "טז"
            else -> TENS[n / 10] + ONES[n % 10]
        }
        s = when {
            s.length == 1 -> s + "׳"
            s.length > 1 -> s.dropLast(1) + "״" + s.last()
            else -> s
        }
        if (thousands in 1..9) s = ONES[thousands] + "׳" + s
        return s
    }

    private val MONTHS_HE = arrayOf("", "ניסן", "אייר", "סיון", "תמוז", "אב", "אלול", "תשרי", "חשון", "כסלו", "טבת", "שבט", "אדר", "אדר ב׳")
    private val WEEKDAYS_HE = arrayOf("יום ראשון", "יום שני", "יום שלישי", "יום רביעי", "יום חמישי", "יום שישי", "שבת")

    private val MONTHS = mapOf(
        Lang.FR to arrayOf("", "Nissan", "Iyar", "Sivan", "Tamouz", "Av", "Eloul", "Tichri", "Hechvan", "Kislev", "Tévet", "Chevat", "Adar", "Adar II"),
        Lang.EN to arrayOf("", "Nisan", "Iyar", "Sivan", "Tammuz", "Av", "Elul", "Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", "Adar", "Adar II"),
        Lang.HE to MONTHS_HE,
    )
    private val WEEKDAYS = mapOf(
        Lang.FR to arrayOf("dimanche", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "Chabbat"),
        Lang.EN to arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Shabbat"),
        Lang.HE to WEEKDAYS_HE,
    )

    fun monthHe(d: HebrewDate): String =
        if (d.month == Luach.ADAR && Luach.isLeap(d.year)) "אדר א׳" else MONTHS_HE[d.month]

    fun month(d: HebrewDate, lang: Lang): String = when {
        lang == Lang.HE -> monthHe(d)
        d.month == Luach.ADAR && Luach.isLeap(d.year) -> "Adar I"
        else -> MONTHS.getValue(lang)[d.month]
    }

    fun weekdayHe(wd: Int): String = WEEKDAYS_HE[wd]
    fun weekday(wd: Int, lang: Lang): String = WEEKDAYS.getValue(lang)[wd]

    /** « כ״ה אלול ה׳תשפ״ו » */
    fun dateHe(d: HebrewDate): String = "${gematria(d.day.toLong())} ${monthHe(d)} ${gematria(d.year)}"

    /** « כ״ה אלול » */
    fun dayMonthHe(d: HebrewDate): String = "${gematria(d.day.toLong())} ${monthHe(d)}"

    /** « 25 Eloul 5786 » (chiffres arabes, même en hébreu, comme le site). */
    fun dateTranslit(d: HebrewDate, lang: Lang): String = "${d.day} ${month(d, lang)} ${d.year}"

    /** « lundi · יום שני », ou seulement l'hébreu en hébreu. */
    fun weekdayLine(wd: Int, lang: Lang): String =
        if (lang == Lang.HE) WEEKDAYS_HE[wd] else "${weekday(wd, lang)} · ${WEEKDAYS_HE[wd]}"

    fun pad(n: Int, width: Int): String = n.toString().padStart(width, '0')

    /** Groupes de trois chiffres séparés par une espace fine insécable. */
    fun group(n: Long): String = n.toString().reversed().chunked(3).joinToString(" ").reversed()
}
