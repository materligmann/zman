// Noms des fêtes, dans les translittérations du reste de l'app.

extension Feast {
    public func name(_ lang: Lang) -> String {
        let n = Feast.names[self]!
        switch lang {
        case .fr: return n.0
        case .en: return n.1
        case .he: return n.2
        }
    }

    public var nameHe: String { name(.he) }

    static let names: [Feast: (String, String, String)] = [
        .roshHashana: ("Roch Hachana", "Rosh Hashana", "ראש השנה"),
        .tzomGedalia: ("Jeûne de Guedalia", "Fast of Gedaliah", "צום גדליה"),
        .yomKippur: ("Yom Kippour", "Yom Kippur", "יום כיפור"),
        .sukkot: ("Souccot", "Sukkot", "סוכות"),
        .cholHamoedSukkot: ("Hol hamoed Souccot", "Chol HaMoed Sukkot", "חול המועד סוכות"),
        .hoshanaRabba: ("Hochaana Rabba", "Hoshana Rabbah", "הושענא רבה"),
        .sheminiAtzeret: ("Chemini Atseret", "Shemini Atzeret", "שמיני עצרת"),
        .simchatTorah: ("Simhat Torah", "Simchat Torah", "שמחת תורה"),
        .sheminiAtzeretSimchatTorah: ("Chemini Atseret · Simhat Torah", "Shemini Atzeret · Simchat Torah", "שמיני עצרת · שמחת תורה"),
        .chanukah: ("Hanoucca", "Chanukah", "חנוכה"),
        .asaraBeTevet: ("Jeûne du 10 Tévet", "Fast of 10 Tevet", "עשרה בטבת"),
        .tuBishvat: ("Tou Bichvat", "Tu BiShvat", "ט״ו בשבט"),
        .purimKatan: ("Pourim Katan", "Purim Katan", "פורים קטן"),
        .taanitEsther: ("Jeûne d’Esther", "Fast of Esther", "תענית אסתר"),
        .purim: ("Pourim", "Purim", "פורים"),
        .shushanPurim: ("Chouchan Pourim", "Shushan Purim", "שושן פורים"),
        .pesach: ("Pessah", "Pesach", "פסח"),
        .cholHamoedPesach: ("Hol hamoed Pessah", "Chol HaMoed Pesach", "חול המועד פסח"),
        .shviiShelPesach: ("Septième jour de Pessah", "Seventh day of Pesach", "שביעי של פסח"),
        .yomHashoah: ("Yom HaChoah", "Yom HaShoah", "יום השואה"),
        .yomHazikaron: ("Yom HaZikaron", "Yom HaZikaron", "יום הזיכרון"),
        .yomHaatzmaut: ("Yom HaAtsmaout", "Yom HaAtzmaut", "יום העצמאות"),
        .pesachSheni: ("Pessah Chéni", "Pesach Sheni", "פסח שני"),
        .lagBaomer: ("Lag BaOmer", "Lag BaOmer", "ל״ג בעומר"),
        .yomYerushalayim: ("Yom Yerouchalayim", "Yom Yerushalayim", "יום ירושלים"),
        .shavuot: ("Chavouot", "Shavuot", "שבועות"),
        .tzomTammuz: ("Jeûne du 17 Tamouz", "Fast of 17 Tammuz", "צום י״ז בתמוז"),
        .tishaBeAv: ("Ticha BeAv", "Tisha BeAv", "תשעה באב"),
        .tuBeAv: ("Tou BeAv", "Tu BeAv", "ט״ו באב"),
        .roshChodesh: ("Roch Hodech", "Rosh Chodesh", "ראש חודש"),
    ]
}

extension Observance {
    /// « Roch Hodech Hechvan » ; le nom seul pour les autres fêtes.
    public func name(_ lang: Lang, year: Int64) -> String {
        guard feast == .roshChodesh, let month else { return feast.name(lang) }
        return "\(feast.name(lang)) \(Names.month(HebrewDate(year: year, month: month, day: 1), lang))"
    }

    public func nameHe(year: Int64) -> String { name(.he, year: year) }
}
