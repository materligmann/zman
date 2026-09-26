import Foundation

// Textes de l'interface, repris de web/content/*/index.html et web/i18n/*.json.

public struct Strings: Sendable {
    public let hour, chelek, rega: String
    public let regaimSinceEpoch: String
    public let syncing: String
    public let why: String
    public let note: String
    public let measured, predicted: String
    let dataAge: String
    public let fallback: String
    public let offline: String
    public let neverSynced: String
    public let widgetName: String
    public let widgetDescription: String
    public let hourOf: String
    public let privacy: String
    public let widgetTitle: String
    public let widgetHint: String

    public static func of(_ lang: Lang) -> Strings {
        switch lang {
        case .fr: return fr
        case .en: return en
        case .he: return he
        }
    }

    public func quality(_ c: Calibration?, offline isOffline: Bool) -> String {
        guard let c else { return isOffline ? neverSynced : syncing }
        if isOffline { return offline }
        if c.source != "iers" { return fallback }
        return dataAge
            .replacingOccurrences(of: "{n}", with: String(Int(c.dut1AgeDays.rounded())))
            .replacingOccurrences(of: "{state}", with: c.predicted ? predicted : measured)
    }

    /// « heure 22 » / « hour 22 » / « שעה 22 »
    public func hourLabel(_ h: Int) -> String { hourOf.replacingOccurrences(of: "{n}", with: String(h)) }

    static let fr = Strings(
        hour: "heure", chelek: "chelek", rega: "rega",
        regaimSinceEpoch: "rega’im depuis l’epoch",
        syncing: "Synchronisation…",
        why: "Pourquoi ce compteur est différent de votre montre →",
        note: "Entre deux synchronisations, l’appareil interpole ; la référence est le serveur, qui suit la rotation terrestre (UT1).",
        measured: "mesurée", predicted: "prédite",
        dataAge: "Donnée IERS vieille de {n} jours, {state}.",
        fallback: "Sans donnée IERS : UT1 approché par l’horloge de la machine.",
        offline: "Serveur injoignable : interpolation locale seulement.",
        neverSynced: "Jamais synchronisé : horloge de l’appareil seule (UT1 ≈ UTC).",
        widgetName: "Zman",
        widgetDescription: "La date hébraïque et l’heure du jour, en temps de rotation.",
        hourOf: "heure {n}",
        privacy: "Confidentialité",
        widgetTitle: "Le widget",
        widgetHint: "Ajoutez Zman à l’écran d’accueil ou à l’écran verrouillé : touchez longuement le fond d’écran, puis « Modifier » › « Ajouter un widget », et cherchez Zman."
    )

    static let en = Strings(
        hour: "hour", chelek: "chelek", rega: "rega",
        regaimSinceEpoch: "rega’im since the epoch",
        syncing: "Synchronising…",
        why: "Why this counter differs from your watch →",
        note: "Between two synchronisations the device interpolates; the reference is the server, which follows the Earth’s rotation (UT1).",
        measured: "measured", predicted: "predicted",
        dataAge: "IERS data is {n} days old, {state}.",
        fallback: "No IERS data: UT1 approximated by the machine clock.",
        offline: "Server unreachable: local interpolation only.",
        neverSynced: "Never synchronised: device clock only (UT1 ≈ UTC).",
        widgetName: "Zman",
        widgetDescription: "The Hebrew date and the hour of the day, in rotation time.",
        hourOf: "hour {n}",
        privacy: "Privacy",
        widgetTitle: "The widget",
        widgetHint: "Add Zman to your Home Screen or Lock Screen: touch and hold the wallpaper, tap Edit › Add Widget, and search for Zman."
    )

    static let he = Strings(
        hour: "שעה", chelek: "חלק", rega: "רגע",
        regaimSinceEpoch: "רגעים מאז ראשית המניין",
        syncing: "מסתנכרן…",
        why: "למה המונה הזה שונה מהשעון שלכם ←",
        note: "בין שני סנכרונים המכשיר משלים בעצמו; הייחוס הוא השרת, העוקב אחר סיבוב כדור הארץ (UT1).",
        measured: "מדודים", predicted: "חזויים",
        dataAge: "נתוני IERS בני {n} ימים, {state}.",
        fallback: "ללא נתוני IERS:‏ UT1 מקורב לפי שעון המחשב.",
        offline: "השרת אינו זמין: אינטרפולציה מקומית בלבד.",
        neverSynced: "לא סונכרן מעולם: שעון המכשיר בלבד (UT1 ≈ UTC).",
        widgetName: "זמן",
        widgetDescription: "התאריך העברי ושעת היום, בזמן הסיבוב.",
        hourOf: "שעה {n}",
        privacy: "פרטיות",
        widgetTitle: "הווידג׳ט",
        widgetHint: "הוסיפו את זמן למסך הבית או למסך הנעילה: לחיצה ארוכה על הרקע, ואז „עריכה” ‹ „הוספת ווידג׳ט”, וחפשו Zman."
    )
}

extension Lang {
    public static var current: Lang { preferred(Locale.preferredLanguages) }
    public var isRTL: Bool { self == .he }
    /// Chemin de la page du site dans cette langue.
    public func siteURL(_ path: String) -> URL {
        ZmanAPI.base.appendingPathComponent(rawValue).appendingPathComponent(path)
    }
}
