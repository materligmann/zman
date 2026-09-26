// Noms et nombres, repris de web/i18n/*.json et de web/static/js/clock.js.

public enum Lang: String, CaseIterable, Sendable {
    case fr, en, he

    /// Langue d'interface à partir des langues préférées du système.
    public static func preferred(_ languages: [String]) -> Lang {
        for l in languages {
            let code = l.prefix(2).lowercased()
            if code == "iw" { return .he }
            if let lang = Lang(rawValue: code) { return lang }
        }
        return .fr
    }
}

public enum Names {
    static let ones = ["", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט"]
    static let tens = ["", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ"]
    static let hundreds = ["", "ק", "ר", "ש", "ת", "תק", "תר", "תש", "תת", "תתק"]

    /// Nombre hébraïque avec gershayim (jours, années).
    public static func gematria(_ value: Int64) -> String {
        var n = Int(value)
        let thousands = n / 1000
        n %= 1000
        var s = hundreds[n / 100]
        n %= 100
        if n == 15 { s += "טו" } else if n == 16 { s += "טז" } else { s += tens[n / 10] + ones[n % 10] }
        if s.count == 1 { s += "׳" } else if s.count > 1 { s.insert("״", at: s.index(before: s.endIndex)) }
        if thousands > 0 && thousands < 10 { s = ones[thousands] + "׳" + s }
        return s
    }

    static let monthsHe = ["", "ניסן", "אייר", "סיון", "תמוז", "אב", "אלול", "תשרי", "חשון", "כסלו", "טבת", "שבט", "אדר", "אדר ב׳"]
    static let weekdaysHe = ["יום ראשון", "יום שני", "יום שלישי", "יום רביעי", "יום חמישי", "יום שישי", "שבת"]

    static let months: [Lang: [String]] = [
        .fr: ["", "Nissan", "Iyar", "Sivan", "Tamouz", "Av", "Eloul", "Tichri", "Hechvan", "Kislev", "Tévet", "Chevat", "Adar", "Adar II"],
        .en: ["", "Nisan", "Iyar", "Sivan", "Tammuz", "Av", "Elul", "Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", "Adar", "Adar II"],
        .he: monthsHe,
    ]
    static let weekdays: [Lang: [String]] = [
        .fr: ["dimanche", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "Chabbat"],
        .en: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Shabbat"],
        .he: weekdaysHe,
    ]

    public static func monthHe(_ d: HebrewDate) -> String {
        d.month == Luach.adar && Luach.isLeap(d.year) ? "אדר א׳" : monthsHe[d.month]
    }

    public static func month(_ d: HebrewDate, _ lang: Lang) -> String {
        if lang == .he { return monthHe(d) }
        if d.month == Luach.adar && Luach.isLeap(d.year) { return "Adar I" }
        return months[lang]![d.month]
    }

    public static func weekdayHe(_ wd: Int) -> String { weekdaysHe[wd] }
    public static func weekday(_ wd: Int, _ lang: Lang) -> String { weekdays[lang]![wd] }

    /// « כ״ה אלול ה׳תשפ״ו »
    public static func dateHe(_ d: HebrewDate) -> String {
        "\(gematria(Int64(d.day))) \(monthHe(d)) \(gematria(d.year))"
    }

    /// « כ״ה אלול »
    public static func dayMonthHe(_ d: HebrewDate) -> String {
        "\(gematria(Int64(d.day))) \(monthHe(d))"
    }

    /// « 25 Eloul 5786 » (chiffres arabes, même en hébreu, comme le site).
    public static func dateTranslit(_ d: HebrewDate, _ lang: Lang) -> String {
        "\(d.day) \(month(d, lang)) \(d.year)"
    }

    /// « lundi · יום שני », ou seulement l'hébreu en hébreu.
    public static func weekdayLine(_ wd: Int, _ lang: Lang) -> String {
        lang == .he ? weekdaysHe[wd] : "\(weekday(wd, lang)) · \(weekdaysHe[wd])"
    }

    public static func pad(_ n: Int, _ width: Int) -> String {
        let s = String(n)
        return String(repeating: "0", count: max(0, width - s.count)) + s
    }

    /// Groupes de trois chiffres séparés par une espace fine insécable.
    public static func group(_ n: Int64) -> String {
        var out = ""
        for (i, c) in String(n).reversed().enumerated() {
            if i > 0 && i % 3 == 0 && c != "-" { out.append("\u{202F}") }
            out.append(c)
        }
        return String(out.reversed())
    }
}
