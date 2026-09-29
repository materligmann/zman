package technology.zman.core

// Noms des fêtes, dans les translittérations du reste de l'app.

private val FEAST_NAMES: Map<Feast, Triple<String, String, String>> = mapOf(
    Feast.ROSH_HASHANA to Triple("Roch Hachana", "Rosh Hashana", "ראש השנה"),
    Feast.TZOM_GEDALIA to Triple("Jeûne de Guedalia", "Fast of Gedaliah", "צום גדליה"),
    Feast.YOM_KIPPUR to Triple("Yom Kippour", "Yom Kippur", "יום כיפור"),
    Feast.SUKKOT to Triple("Souccot", "Sukkot", "סוכות"),
    Feast.CHOL_HAMOED_SUKKOT to Triple("Hol hamoed Souccot", "Chol HaMoed Sukkot", "חול המועד סוכות"),
    Feast.HOSHANA_RABBA to Triple("Hochaana Rabba", "Hoshana Rabbah", "הושענא רבה"),
    Feast.SHEMINI_ATZERET to Triple("Chemini Atseret", "Shemini Atzeret", "שמיני עצרת"),
    Feast.SIMCHAT_TORAH to Triple("Simhat Torah", "Simchat Torah", "שמחת תורה"),
    Feast.SHEMINI_ATZERET_SIMCHAT_TORAH to Triple("Chemini Atseret · Simhat Torah", "Shemini Atzeret · Simchat Torah", "שמיני עצרת · שמחת תורה"),
    Feast.CHANUKAH to Triple("Hanoucca", "Chanukah", "חנוכה"),
    Feast.ASARA_BE_TEVET to Triple("Jeûne du 10 Tévet", "Fast of 10 Tevet", "עשרה בטבת"),
    Feast.TU_BISHVAT to Triple("Tou Bichvat", "Tu BiShvat", "ט״ו בשבט"),
    Feast.PURIM_KATAN to Triple("Pourim Katan", "Purim Katan", "פורים קטן"),
    Feast.TAANIT_ESTHER to Triple("Jeûne d’Esther", "Fast of Esther", "תענית אסתר"),
    Feast.PURIM to Triple("Pourim", "Purim", "פורים"),
    Feast.SHUSHAN_PURIM to Triple("Chouchan Pourim", "Shushan Purim", "שושן פורים"),
    Feast.PESACH to Triple("Pessah", "Pesach", "פסח"),
    Feast.CHOL_HAMOED_PESACH to Triple("Hol hamoed Pessah", "Chol HaMoed Pesach", "חול המועד פסח"),
    Feast.SHVII_SHEL_PESACH to Triple("Septième jour de Pessah", "Seventh day of Pesach", "שביעי של פסח"),
    Feast.YOM_HASHOAH to Triple("Yom HaChoah", "Yom HaShoah", "יום השואה"),
    Feast.YOM_HAZIKARON to Triple("Yom HaZikaron", "Yom HaZikaron", "יום הזיכרון"),
    Feast.YOM_HAATZMAUT to Triple("Yom HaAtsmaout", "Yom HaAtzmaut", "יום העצמאות"),
    Feast.PESACH_SHENI to Triple("Pessah Chéni", "Pesach Sheni", "פסח שני"),
    Feast.LAG_BAOMER to Triple("Lag BaOmer", "Lag BaOmer", "ל״ג בעומר"),
    Feast.YOM_YERUSHALAYIM to Triple("Yom Yerouchalayim", "Yom Yerushalayim", "יום ירושלים"),
    Feast.SHAVUOT to Triple("Chavouot", "Shavuot", "שבועות"),
    Feast.TZOM_TAMMUZ to Triple("Jeûne du 17 Tamouz", "Fast of 17 Tammuz", "צום י״ז בתמוז"),
    Feast.TISHA_BE_AV to Triple("Ticha BeAv", "Tisha BeAv", "תשעה באב"),
    Feast.TU_BE_AV to Triple("Tou BeAv", "Tu BeAv", "ט״ו באב"),
    Feast.ROSH_CHODESH to Triple("Roch Hodech", "Rosh Chodesh", "ראש חודש"),
)

fun Feast.name(lang: Lang): String {
    val n = FEAST_NAMES.getValue(this)
    return when (lang) {
        Lang.FR -> n.first
        Lang.EN -> n.second
        Lang.HE -> n.third
    }
}

val Feast.nameHe: String get() = name(Lang.HE)

/** « Roch Hodech Hechvan » ; le nom seul pour les autres fêtes. */
fun Observance.name(lang: Lang, year: Long): String {
    val m = month
    if (feast != Feast.ROSH_CHODESH || m == null) return feast.name(lang)
    return "${feast.name(lang)} ${Names.month(HebrewDate(year, m, 1), lang)}"
}

fun Observance.nameHe(year: Long): String = name(Lang.HE, year)
