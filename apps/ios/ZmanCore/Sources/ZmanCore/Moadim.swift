import Foundation

// Fêtes, jeûnes et Roch Hodech d'une année, par les règles du calendrier fixe
// (reports de jeûnes compris), en numéros de jours. Aucune table : tout se
// déduit de Luach.

public enum Feast: String, CaseIterable, Sendable {
    case roshHashana, tzomGedalia, yomKippur, sukkot, cholHamoedSukkot, hoshanaRabba
    case sheminiAtzeret, simchatTorah, sheminiAtzeretSimchatTorah
    case chanukah, asaraBeTevet, tuBishvat, purimKatan, taanitEsther, purim, shushanPurim
    case pesach, cholHamoedPesach, shviiShelPesach
    case yomHashoah, yomHazikaron, yomHaatzmaut, pesachSheni, lagBaomer, yomYerushalayim
    case shavuot, tzomTammuz, tishaBeAv, tuBeAv
    case roshChodesh

    public enum Kind: Sendable { case yomTov, cholHamoed, fast, minor, modern, roshChodesh }

    public var kind: Kind {
        switch self {
        case .roshHashana, .yomKippur, .sukkot, .sheminiAtzeret, .simchatTorah, .sheminiAtzeretSimchatTorah,
             .pesach, .shviiShelPesach, .shavuot:
            return .yomTov
        case .cholHamoedSukkot, .hoshanaRabba, .cholHamoedPesach: return .cholHamoed
        case .tzomGedalia, .asaraBeTevet, .taanitEsther, .tzomTammuz, .tishaBeAv: return .fast
        case .yomHashoah, .yomHazikaron, .yomHaatzmaut, .yomYerushalayim: return .modern
        case .roshChodesh: return .roshChodesh
        default: return .minor
        }
    }
}

/// Une fête dans le calendrier : premier jour et nombre de jours.
public struct Observance: Hashable, Sendable {
    public let feast: Feast
    public let start: Int64
    public let length: Int
    /// Mois dont c'est le Roch Hodech (pour .roshChodesh seulement).
    public let month: Int?

    public init(_ feast: Feast, start: Int64, length: Int = 1, month: Int? = nil) {
        self.feast = feast; self.start = start; self.length = length; self.month = month
    }

    public var end: Int64 { start + Int64(length) }
    public func contains(_ day: Int64) -> Bool { day >= start && day < end }
}

public enum Moadim {
    /// Toutes les observances de l'année (1 Tichri → 29 Eloul), dans l'ordre.
    /// `israel` : un seul jour de yom tov (pas de יום טוב שני של גלויות).
    public static func year(_ year: Int64, israel: Bool) -> [Observance] {
        cache.get(year, israel) { compute(year, israel: israel) }
    }

    /// L'horloge redemande l'année à chaque image : on garde les années déjà calculées.
    private static let cache = YearCache()

    private final class YearCache: @unchecked Sendable {
        private var years: [Int64: [Observance]] = [:]
        private let lock = NSLock()

        func get(_ year: Int64, _ israel: Bool, _ make: () -> [Observance]) -> [Observance] {
            let key = israel ? year : -year
            lock.lock(); defer { lock.unlock() }
            if let hit = years[key] { return hit }
            let list = make()
            years[key] = list
            return list
        }
    }

    private static func compute(_ year: Int64, israel: Bool) -> [Observance] {
        guard year >= 1 else { return [] }
        func d(_ month: Int, _ day: Int) -> Int64 { Luach.day(of: HebrewDate(year: year, month: month, day: day))! }
        func wd(_ day: Int64) -> Int { Luach.weekday(day) }
        /// Un jeûne qui tombe le Chabbat est repoussé au dimanche.
        func deferred(_ day: Int64) -> Int64 { wd(day) == Luach.shabbat ? day + 1 : day }
        let yt = israel ? 1 : 2
        let leap = Luach.isLeap(year)
        let adar = leap ? Luach.adarII : Luach.adar
        let t = Luach.tishrei, n = Luach.nissan

        var out: [Observance] = [
            Observance(.roshHashana, start: d(t, 1), length: 2),
            Observance(.tzomGedalia, start: deferred(d(t, 3))),
            Observance(.yomKippur, start: d(t, 10)),
            Observance(.sukkot, start: d(t, 15), length: yt),
            Observance(.cholHamoedSukkot, start: d(t, 15) + Int64(yt), length: 6 - yt),
            Observance(.hoshanaRabba, start: d(t, 21)),
        ]
        if israel {
            out.append(Observance(.sheminiAtzeretSimchatTorah, start: d(t, 22)))
        } else {
            out.append(Observance(.sheminiAtzeret, start: d(t, 22)))
            out.append(Observance(.simchatTorah, start: d(t, 23)))
        }
        out.append(Observance(.chanukah, start: d(Luach.kislev, 25), length: 8))
        out.append(Observance(.asaraBeTevet, start: d(Luach.tevet, 10)))
        out.append(Observance(.tuBishvat, start: d(Luach.shevat, 15)))
        if leap { out.append(Observance(.purimKatan, start: d(Luach.adar, 14))) }
        // Ta'anit Esther ne peut être repoussé après Pourim : avancé au jeudi.
        let esther = d(adar, 13)
        out.append(Observance(.taanitEsther, start: wd(esther) == Luach.shabbat ? esther - 2 : esther))
        out.append(Observance(.purim, start: d(adar, 14)))
        out.append(Observance(.shushanPurim, start: d(adar, 15)))
        out.append(Observance(.pesach, start: d(n, 15), length: yt))
        out.append(Observance(.cholHamoedPesach, start: d(n, 15) + Int64(yt), length: 6 - yt))
        out.append(Observance(.shviiShelPesach, start: d(n, 21), length: yt))

        // Jours de l'État d'Israël, avec leurs reports autour du Chabbat.
        if year >= 5711 {
            let shoah = d(n, 27)
            switch wd(shoah) {
            case Luach.friday: out.append(Observance(.yomHashoah, start: shoah - 1))
            case Luach.sunday: out.append(Observance(.yomHashoah, start: shoah + 1))
            default: out.append(Observance(.yomHashoah, start: shoah))
            }
        }
        if year >= 5708 {
            var atzmaut = d(Luach.iyar, 5)
            switch wd(atzmaut) {
            case Luach.friday: atzmaut -= 1
            case Luach.shabbat: atzmaut -= 2
            case Luach.monday where year >= 5764: atzmaut += 1
            default: break
            }
            out.append(Observance(.yomHazikaron, start: atzmaut - 1))
            out.append(Observance(.yomHaatzmaut, start: atzmaut))
        }
        out.append(Observance(.pesachSheni, start: d(Luach.iyar, 14)))
        out.append(Observance(.lagBaomer, start: d(Luach.iyar, 18)))
        if year >= 5728 { out.append(Observance(.yomYerushalayim, start: d(Luach.iyar, 28))) }
        out.append(Observance(.shavuot, start: d(Luach.sivan, 6), length: yt))
        out.append(Observance(.tzomTammuz, start: deferred(d(Luach.tammuz, 17))))
        out.append(Observance(.tishaBeAv, start: deferred(d(Luach.av, 9))))
        out.append(Observance(.tuBeAv, start: d(Luach.av, 15)))

        // Roch Hodech : le 30 du mois précédent quand il existe, et le 1er.
        let months = Luach.monthsInOrder(year)
        for (i, m) in months.enumerated() where i > 0 {
            let first = d(m, 1)
            let two = Luach.monthLength(year, months[i - 1]) == 30
            out.append(Observance(.roshChodesh, start: two ? first - 1 : first, length: two ? 2 : 1, month: m))
        }
        return out.sorted { ($0.start, $0.length) < ($1.start, $1.length) }
    }

    /// Les observances qui couvrent ce jour.
    public static func on(_ day: Int64, israel: Bool) -> [Observance] {
        guard let date = Luach.date(ofDay: day) else { return [] }
        // Roch Hodech en dernier : « Hanoucca » avant « Roch Hodech Tévet ».
        let list = year(date.year, israel: israel).filter { $0.contains(day) }
        return list.filter { $0.feast != .roshChodesh } + list.filter { $0.feast == .roshChodesh }
    }

    /// Jour du compte de l'omer (1 … 49), du 16 Nissan à la veille de Chavouot.
    public static func omer(_ day: Int64) -> Int? {
        guard let date = Luach.date(ofDay: day),
              let start = Luach.day(of: HebrewDate(year: date.year, month: Luach.nissan, day: 16)) else { return nil }
        let n = day - start + 1
        return (1...49).contains(n) ? Int(n) : nil
    }
}

/// La structure d'une année : longueur, caractère, keviah, place dans le cycle.
public struct YearInfo: Sendable {
    public let year: Int64
    public let isLeap: Bool
    public let length: Int
    public let roshHashana: Int64
    public let pesach: Int64

    public enum Kind: Sendable { case deficient, regular, complete }

    public init(_ year: Int64) {
        self.year = year
        isLeap = Luach.isLeap(year)
        length = Luach.yearLength(year)
        roshHashana = Luach.roshHashana(year)
        pesach = Luach.day(of: HebrewDate(year: year, month: Luach.nissan, day: 15))!
    }

    /// חסרה (353/383), כסדרה (354/384), שלמה (355/385).
    public var kind: Kind {
        switch length % 10 {
        case 3: return .deficient
        case 4: return .regular
        default: return .complete
        }
    }

    /// Cycle de 19 ans (מחזור) et rang de l'année dans le cycle.
    public var cycle: Int64 { (year - 1) / 19 + 1 }
    public var yearInCycle: Int { Int((year - 1) % 19) + 1 }

    /// La keviah, p. ex. « זחא » : jour de Roch Hachana, caractère, jour de Pessah.
    public var keviah: String {
        let letters = ["א", "ב", "ג", "ד", "ה", "ו", "ז"]
        let k: String
        switch kind {
        case .deficient: k = "ח"
        case .regular: k = "כ"
        case .complete: k = "ש"
        }
        return letters[Luach.weekday(roshHashana)] + k + letters[Luach.weekday(pesach)]
    }

    /// Mois dans l'ordre de l'année, avec leur longueur et leur premier jour.
    public var months: [(month: Int, length: Int, firstDay: Int64)] {
        var day = roshHashana
        return Luach.monthsInOrder(year).map { m in
            let l = Luach.monthLength(year, m)
            defer { day += Int64(l) }
            return (m, l, day)
        }
    }
}

/// Le molad au format des annonces (ברכת החודש) : jour de la semaine, heure
/// d'horloge depuis minuit, minutes et chalakim (18 chalakim par minute).
public struct MoladAnnouncement: Sendable {
    /// Jour halachique du molad (le jour commence à 18:00).
    public let weekday: Int
    /// true si le molad tombe entre 18:00 et minuit : « la veille au soir ».
    public let evening: Bool
    public let clockHour: Int
    public let minute: Int
    public let chalakim: Int

    public init(rega r: Int64) {
        let p = Rega.split(r)
        weekday = Luach.weekday(p.day)
        evening = p.hour < 6
        clockHour = (p.hour + 18) % 24
        minute = p.chelek / 18
        chalakim = p.chelek % 18
    }
}
