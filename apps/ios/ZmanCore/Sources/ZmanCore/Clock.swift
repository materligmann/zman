import Foundation

// Portage de pkg/clock : le seul pont entre l'horloge de l'appareil (UTC,
// seconde SI) et les rega'im. Sans DUT1, c'est l'horloge « fallback » du
// serveur ; la calibration contre /api/now y ajoute la correction UT1 (et
// corrige au passage une horloge d'appareil déréglée).

public enum Bridge {
    /// Epoch → epoch Unix : 2 092 591,25 jours, en millisecondes.
    static let epochToUnixMillis: Int64 = 180_799_884_000_000
    /// Longitude de Jérusalem, 35,2137° E → +8 451,288 s.
    static let jerusalemOffsetMillis: Int64 = 8_451_288
    /// 1 969 920 rega'im / 86 400 000 ms = 228 / 10 000.
    static let num: Int64 = 228
    static let den: Int64 = 10_000

    /// Rega'im depuis l'epoch pour un instant Unix en millisecondes (UT1 ≈ UTC).
    public static func rega(unixMillis ms: Int64) -> Int64 {
        let total = ms + jerusalemOffsetMillis + epochToUnixMillis
        let q = floorDiv(total, den)
        return q * num + (total - q * den) * num / den
    }

    /// Premier instant Unix (ms) où le compteur atteint r : inverse de rega(unixMillis:).
    public static func unixMillis(rega r: Int64) -> Int64 {
        let a = floorDiv(r, num)
        let b = r - a * num
        let total = a * den + (b * den + num - 1) / num
        return total - jerusalemOffsetMillis - epochToUnixMillis
    }

    public static func unixMillis(_ date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1000).rounded(.down))
    }
}

/// Correction mesurée contre le serveur, et qualité annoncée par lui.
public struct Calibration: Codable, Equatable, Sendable {
    public var offsetRegaim: Int64      // rega serveur − rega local (DUT1 + erreur de l'horloge locale)
    public var syncedAt: Date
    public var source: String           // "iers" ou "fallback"
    public var dut1AgeDays: Double
    public var predicted: Bool

    public init(offsetRegaim: Int64, syncedAt: Date, source: String, dut1AgeDays: Double, predicted: Bool) {
        self.offsetRegaim = offsetRegaim; self.syncedAt = syncedAt
        self.source = source; self.dut1AgeDays = dut1AgeDays; self.predicted = predicted
    }
}

/// Un instant décomposé.
public struct Moment: Equatable, Sendable {
    public let rega: Int64
    public let day: Int64
    public let hour: Int
    public let chelek: Int
    public let regaInChelek: Int
    public let date: HebrewDate?
    public let weekday: Int

    public init(rega r: Int64) {
        let p = Rega.split(r)
        rega = r; day = p.day; hour = p.hour; chelek = p.chelek; regaInChelek = p.rega
        date = Luach.date(ofDay: p.day)
        weekday = Luach.weekday(p.day)
    }

    /// Premier rega de l'heure courante et de la suivante.
    public var hourStart: Int64 { Rega.dayStart(day) + Int64(hour) * Rega.perHour }
    public var hourEnd: Int64 { hourStart + Rega.perHour }
    public var dayStart: Int64 { Rega.dayStart(day) }
    public var dayEnd: Int64 { Rega.dayStart(day + 1) }
}

public struct ZmanClock: Sendable {
    public var calibration: Calibration?

    public init(calibration: Calibration?) { self.calibration = calibration }

    public func rega(at date: Date = Date()) -> Int64 {
        Bridge.rega(unixMillis: Bridge.unixMillis(date)) + (calibration?.offsetRegaim ?? 0)
    }

    public func moment(at date: Date = Date()) -> Moment { Moment(rega: rega(at: date)) }

    /// Instant de l'appareil où le compteur atteint r.
    public func date(atRega r: Int64) -> Date {
        let ms = Bridge.unixMillis(rega: r - (calibration?.offsetRegaim ?? 0))
        return Date(timeIntervalSince1970: Double(ms) / 1000)
    }
}
