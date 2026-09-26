// Portage de pkg/luach : calendrier fixe hébraïque par arithmétique pure sur
// des numéros de jours (jour 1 = lundi = 1 Tishrei de l'an 1).

public struct HebrewDate: Equatable, Hashable, Codable, Sendable {
    public let year: Int64
    public let month: Int   // Nissan = 1 … Adar = 12, Adar II = 13
    public let day: Int

    public init(year: Int64, month: Int, day: Int) {
        self.year = year; self.month = month; self.day = day
    }
}

public enum Luach {
    public static let nissan = 1, iyar = 2, sivan = 3, tammuz = 4, av = 5, elul = 6
    public static let tishrei = 7, cheshvan = 8, kislev = 9, tevet = 10, shevat = 11
    public static let adar = 12, adarII = 13

    public static let sunday = 0, monday = 1, tuesday = 2, wednesday = 3
    public static let thursday = 4, friday = 5, shabbat = 6

    static let chalakimPerDay = Rega.chalakimPerDay
    public static let lunarMonthChalakim: Int64 = 29 * chalakimPerDay + 12 * Rega.chalakimPerHour + 793 // 765 433
    public static let moladTohuChalakim: Int64 = 1 * chalakimPerDay + 5 * Rega.chalakimPerHour + 204   // 31 524

    static let moladZakenParts: Int64 = 18 * Rega.chalakimPerHour
    static let gatradParts: Int64 = 9 * Rega.chalakimPerHour + 204
    static let betutekpatParts: Int64 = 15 * Rega.chalakimPerHour + 589

    public static func isLeap(_ year: Int64) -> Bool { mod(7 * year + 1, 19) < 7 }

    public static func monthsInYear(_ year: Int64) -> Int { isLeap(year) ? 13 : 12 }

    static func monthsElapsed(_ year: Int64) -> Int64 { floorDiv(235 * year - 234, 19) }

    public static func moladTishreiChalakim(_ year: Int64) -> Int64 {
        moladTohuChalakim + monthsElapsed(year) * lunarMonthChalakim
    }

    /// Numéro du jour du 1 Tishrei, après les quatre dehiyot.
    public static func roshHashana(_ year: Int64) -> Int64 {
        let m = moladTishreiChalakim(year)
        var day = floorDiv(m, chalakimPerDay)
        let parts = m - day * chalakimPerDay
        let wd = Int(mod(day, 7))

        if parts >= moladZakenParts {
            day += 1 // molad zaken
        } else if wd == tuesday && parts >= gatradParts && !isLeap(year) {
            day += 1 // GaTaRaD
        } else if wd == monday && parts >= betutekpatParts && isLeap(year - 1) {
            day += 1 // BeTU'TeKPaT
        }
        switch Int(mod(day, 7)) {
        case sunday, wednesday, friday: day += 1 // לא אד״ו ראש
        default: break
        }
        return day
    }

    public static func yearLength(_ year: Int64) -> Int {
        Int(roshHashana(year + 1) - roshHashana(year))
    }

    public static func monthLength(_ year: Int64, _ month: Int) -> Int {
        switch month {
        case nissan, sivan, av, tishrei, shevat: return 30
        case iyar, tammuz, elul, tevet: return 29
        case cheshvan: return yearLength(year) % 10 == 5 ? 30 : 29
        case kislev: return yearLength(year) % 10 == 3 ? 29 : 30
        case adar: return isLeap(year) ? 30 : 29
        case adarII: return isLeap(year) ? 29 : 0
        default: return 0
        }
    }

    public static func monthsInOrder(_ year: Int64) -> [Int] {
        isLeap(year)
            ? [tishrei, cheshvan, kislev, tevet, shevat, adar, adarII, nissan, iyar, sivan, tammuz, av, elul]
            : [tishrei, cheshvan, kislev, tevet, shevat, adar, nissan, iyar, sivan, tammuz, av, elul]
    }

    public static func weekday(_ day: Int64) -> Int { Int(mod(day, 7)) }

    /// Numéro du jour d'une date, ou nil si la date n'existe pas.
    public static func day(of date: HebrewDate) -> Int64? {
        guard date.year >= 1, let idx = monthsInOrder(date.year).firstIndex(of: date.month),
              date.day >= 1, date.day <= monthLength(date.year, date.month) else { return nil }
        var d = roshHashana(date.year)
        for m in monthsInOrder(date.year)[..<idx] { d += Int64(monthLength(date.year, m)) }
        return d + Int64(date.day) - 1
    }

    /// Date du jour donné, ou nil avant le 1 Tishrei 1.
    public static func date(ofDay day: Int64) -> HebrewDate? {
        guard day >= 1 else { return nil }
        var year = max(1, day * 19 / 6940 + 1)
        while roshHashana(year) > day { year -= 1 }
        while roshHashana(year + 1) <= day { year += 1 }
        var rem = day - roshHashana(year)
        for m in monthsInOrder(year) {
            let l = Int64(monthLength(year, m))
            if rem < l { return HebrewDate(year: year, month: m, day: Int(rem) + 1) }
            rem -= l
        }
        return nil
    }

    /// Molad du mois, en rega'im depuis l'epoch.
    public static func molad(year: Int64, month: Int) -> Int64? {
        guard year >= 1, let idx = monthsInOrder(year).firstIndex(of: month) else { return nil }
        return Rega.fromChalakim(moladTishreiChalakim(year) + Int64(idx) * lunarMonthChalakim)
    }
}
