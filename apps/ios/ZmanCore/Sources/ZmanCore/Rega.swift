// Portage de pkg/rega : l'unité fondamentale, le rega (1/76 de chelek), et
// la décomposition d'un nombre de rega'im depuis l'epoch.

public enum Rega {
    public static let perChelek: Int64 = 76     // Rambam, Hilchot Kiddush HaChodesh 10:1
    public static let chalakimPerHour: Int64 = 1080
    public static let hoursPerDay: Int64 = 24

    public static let perHour: Int64 = perChelek * chalakimPerHour   // 82 080
    public static let chalakimPerDay: Int64 = chalakimPerHour * hoursPerDay // 25 920
    public static let perDay: Int64 = perChelek * chalakimPerDay     // 1 969 920

    /// Décompose r en (jour, heure, chelek, rega), composantes toujours
    /// dans [0, borne) même pour r négatif (division plancher).
    public static func split(_ r: Int64) -> (day: Int64, hour: Int, chelek: Int, rega: Int) {
        let day = floorDiv(r, perDay)
        var rem = r - day * perDay
        let hour = rem / perHour
        rem -= hour * perHour
        let chelek = rem / perChelek
        return (day, Int(hour), Int(chelek), Int(rem - chelek * perChelek))
    }

    public static func day(_ r: Int64) -> Int64 { floorDiv(r, perDay) }
    public static func dayStart(_ day: Int64) -> Int64 { day * perDay }
    public static func fromChalakim(_ ch: Int64) -> Int64 { ch * perChelek }
}

@inlinable func floorDiv(_ a: Int64, _ b: Int64) -> Int64 {
    let q = a / b
    return (a % b != 0) && ((a < 0) != (b < 0)) ? q - 1 : q
}

@inlinable func mod(_ a: Int64, _ b: Int64) -> Int64 { a - floorDiv(a, b) * b }
