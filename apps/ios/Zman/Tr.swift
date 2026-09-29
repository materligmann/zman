import Foundation
import ZmanCore

/// Textes de l'interface des onglets, écrits sur place dans les trois
/// langues : `tr("Fêtes", "Festivals", "מועדים")`.
struct Tr {
    let lang: Lang

    init(_ lang: Lang) { self.lang = lang }

    func callAsFunction(_ fr: String, _ en: String, _ he: String) -> String {
        switch lang {
        case .fr: return fr
        case .en: return en
        case .he: return he
        }
    }

    /// Distance en jours halachiques : « aujourd'hui », « demain », « dans 12 jours », « il y a 3 jours ».
    func days(_ n: Int64) -> String {
        switch n {
        case 0: return self("aujourd’hui", "today", "היום")
        case 1: return self("demain", "tomorrow", "מחר")
        case -1: return self("hier", "yesterday", "אתמול")
        case 2...: return self("dans \(n) jours", "in \(n) days", "בעוד \(n) ימים")
        default: return self("il y a \(-n) jours", "\(-n) days ago", "לפני \(-n) ימים")
        }
    }

    /// Durée en jours, heures, chalakim : « 3 j 14 h 0512 ch ».
    func span(_ regaim: Int64) -> String {
        let r = max(0, regaim)
        let d = r / Rega.perDay
        let h = (r % Rega.perDay) / Rega.perHour
        let ch = (r % Rega.perHour) / Rega.perChelek
        if lang == .he { return "\u{200F}\(d) ימ׳ \u{200F}\(h) שע׳ \u{200F}\(ch) ח׳" }
        let parts = self("j", "d", "ימ׳")
        return "\(d) \(parts) \(h) \(self("h", "h", "שע׳")) \(ch) \(self("ch", "ch", "ח׳"))"
    }

    /// Nombre de jours d'une durée : « 1 jour », « 8 jours ».
    func dayCount(_ n: Int) -> String {
        n == 1 ? self("1 jour", "1 day", "יום אחד") : self("\(n) jours", "\(n) days", "\(n) ימים")
    }

    /// Le molad au format des annonces : « lundi 12:10 et 7 chalakim », « vendredi soir 20:59 et 1 chelek ».
    func announcement(_ a: MoladAnnouncement) -> String {
        let time = "\(a.clockHour):\(Names.pad(a.minute, 2))"
        let ch: String
        switch lang {
        case .fr: ch = a.chalakim == 1 ? "1 chelek" : "\(a.chalakim) chalakim"
        case .en: ch = a.chalakim == 1 ? "1 chelek" : "\(a.chalakim) chalakim"
        case .he: ch = a.chalakim == 1 ? "חלק אחד" : "\(a.chalakim) חלקים"
        }
        if lang == .he {
            let day = a.evening ? "ליל " + Tr.shortHe[a.weekday] : Names.weekdayHe(a.weekday)
            return "\(day), \(time) ו־\(ch)"
        }
        let day = a.evening
            ? self("\(Names.weekday((a.weekday + 6) % 7, .fr)) soir", "\(Names.weekday((a.weekday + 6) % 7, .en)) evening", "")
            : Names.weekday(a.weekday, lang)
        return "\(day) \(time) \(self("et", "and", "")) \(ch)"
    }

    /// Séparateur « · », encadré de marques RLM en hébreu : sans elles, les
    /// chiffres voisins font glisser le point de l'autre côté.
    var dot: String { lang == .he ? "\u{200F} · \u{200F}" : " · " }

    /// Éléments joints par le séparateur.
    func join(_ parts: String...) -> String {
        parts.filter { !$0.isEmpty }.map { lang == .he ? "\u{200F}" + $0 : $0 }.joined(separator: dot)
    }

    static let shortHe = ["ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת"]

    /// En-têtes de colonnes du calendrier.
    var weekdayInitials: [String] {
        self(
            "dim.|lun.|mar.|mer.|jeu.|ven.|chab.",
            "Sun|Mon|Tue|Wed|Thu|Fri|Shab",
            "א׳|ב׳|ג׳|ד׳|ה׳|ו׳|ש׳"
        ).components(separatedBy: "|")
    }

    /// « 15 Nissan » ou, sur plusieurs jours, « 15 – 22 Nissan » / « 25 Kislev – 2 Tévet ».
    func range(_ o: Observance) -> String {
        if lang == .he { return rangeHe(o) }
        let a = Luach.date(ofDay: o.start)!, b = Luach.date(ofDay: o.end - 1)!
        if o.length == 1 { return Names.dateTranslit(a, lang).dropYear }
        if a.month == b.month { return "\(a.day) – \(b.day) \(Names.month(a, lang))" }
        return "\(a.day) \(Names.month(a, lang)) – \(b.day) \(Names.month(b, lang))"
    }

    func rangeHe(_ o: Observance) -> String {
        let a = Luach.date(ofDay: o.start)!, b = Luach.date(ofDay: o.end - 1)!
        if o.length == 1 { return Names.dayMonthHe(a) }
        if a.month == b.month { return "\(Names.gematria(Int64(a.day)))–\(Names.gematria(Int64(b.day))) \(Names.monthHe(a))" }
        return "\(Names.dayMonthHe(a)) – \(Names.dayMonthHe(b))"
    }

    func omer(_ n: Int) -> String {
        self("Omer : \(n)ᵉ jour", "Omer: day \(n)", "ספירת העומר: יום \(n)")
    }
}

private extension String {
    /// « 15 Nissan 5786 » → « 15 Nissan ».
    var dropYear: String {
        guard let i = lastIndex(of: " ") else { return self }
        return String(self[..<i])
    }
}
