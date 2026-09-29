import Foundation
import Observation
import UserNotifications
import ZmanCore

/// Réglages de l'app, gardés dans les UserDefaults de l'appareil.
@MainActor @Observable
final class Settings {
    private let defaults = UserDefaults.standard

    /// En Israël : un seul jour de yom tov. Par défaut, selon la région de l'appareil.
    var israel: Bool { didSet { defaults.set(israel, forKey: "israel") } }
    var remindRoshChodesh: Bool { didSet { defaults.set(remindRoshChodesh, forKey: "remind.roshChodesh") } }
    var remindFeasts: Bool { didSet { defaults.set(remindFeasts, forKey: "remind.feasts") } }
    var remindMolad: Bool { didSet { defaults.set(remindMolad, forKey: "remind.molad") } }

    init() {
        israel = defaults.object(forKey: "israel") as? Bool ?? (Locale.current.region?.identifier == "IL")
        remindRoshChodesh = defaults.bool(forKey: "remind.roshChodesh")
        remindFeasts = defaults.bool(forKey: "remind.feasts")
        remindMolad = defaults.bool(forKey: "remind.molad")
    }

    var anyReminder: Bool { remindRoshChodesh || remindFeasts || remindMolad }
}

/// Rappels : notifications locales, programmées sur l'appareil. Aucun serveur.
///
/// Une fête est annoncée à la 22ᵉ heure de la veille, deux heures avant que
/// son jour commence (18:00 temps moyen de Jérusalem) ; un molad, à l'instant
/// du molad. iOS garde au plus 64 notifications en attente : on programme
/// les 60 prochaines, et on recommence à chaque ouverture de l'app.
enum Reminders {
    static let limit = 60

    struct Item: Equatable {
        let id: String
        let rega: Int64
        let title: String
        let body: String
    }

    /// Les rappels à venir, à partir de l'instant `now` (en rega'im).
    static func upcoming(from now: Int64, settings: (roshChodesh: Bool, feasts: Bool, molad: Bool, israel: Bool),
                         lang: Lang) -> [Item] {
        let tr = Tr(lang)
        guard let today = Luach.date(ofDay: Rega.day(now)) else { return [] }
        var items: [Item] = []
        for year in [today.year, today.year + 1] {
            for o in Moadim.year(year, israel: settings.israel) {
                let wanted: Bool
                switch o.feast.kind {
                case .roshChodesh: wanted = settings.roshChodesh
                case .cholHamoed: wanted = settings.feasts && o.feast == .hoshanaRabba
                default: wanted = settings.feasts
                }
                guard wanted else { continue }
                let at = Rega.dayStart(o.start) - 2 * Rega.perHour
                let d = Luach.date(ofDay: o.start)!
                let when = "\(Names.weekday(Luach.weekday(o.start), lang)) \(Names.dateTranslit(d, lang))"
                let body = o.length > 1
                    ? tr("Commence ce soir : \(when), pour \(tr.dayCount(o.length)).",
                         "Begins tonight: \(when), for \(tr.dayCount(o.length)).",
                         "מתחיל הערב: \(Names.weekdayHe(Luach.weekday(o.start))), \(Names.dateHe(d)), למשך \(tr.dayCount(o.length)).")
                    : tr("Commence ce soir : \(when).", "Begins tonight: \(when).",
                         "מתחיל הערב: \(Names.weekdayHe(Luach.weekday(o.start))), \(Names.dateHe(d)).")
                items.append(Item(id: "feast.\(o.feast.rawValue).\(o.start)", rega: at,
                                  title: o.name(lang, year: year), body: body))
            }
            if settings.molad {
                for m in Luach.monthsInOrder(year) {
                    guard let r = Luach.molad(year: year, month: m) else { continue }
                    let month = Names.month(HebrewDate(year: year, month: m, day: 1), lang)
                    items.append(Item(id: "molad.\(year).\(m)", rega: r,
                                      title: tr("Molad de \(month)", "Molad of \(month)", "מולד \(month)"),
                                      body: tr("La lune nouvelle, maintenant : \(tr.announcement(MoladAnnouncement(rega: r))).",
                                               "The new moon, now: \(tr.announcement(MoladAnnouncement(rega: r))).",
                                               "המולד עכשיו: \(tr.announcement(MoladAnnouncement(rega: r))).")))
                }
            }
        }
        return Array(items.filter { $0.rega > now }.sorted { $0.rega < $1.rega }.prefix(limit))
    }

    /// Demande l'autorisation ; false si l'utilisateur refuse.
    static func authorize() async -> Bool {
        let center = UNUserNotificationCenter.current()
        let current = await center.notificationSettings()
        switch current.authorizationStatus {
        case .authorized, .provisional, .ephemeral: return true
        case .denied: return false
        default: return (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        }
    }

    /// Remplace tous les rappels en attente par ceux des réglages actuels.
    @MainActor
    static func reschedule(settings s: Settings, clock: ZmanClock, lang: Lang) async {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        guard s.anyReminder else { return }
        let status = await center.notificationSettings().authorizationStatus
        guard status == .authorized || status == .provisional else { return }
        let items = upcoming(from: clock.rega(), settings: (s.remindRoshChodesh, s.remindFeasts, s.remindMolad, s.israel), lang: lang)
        for item in items {
            let content = UNMutableNotificationContent()
            content.title = item.title
            content.body = item.body
            content.sound = .default
            let interval = clock.date(atRega: item.rega).timeIntervalSinceNow
            guard interval > 1 else { continue }
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)
            try? await center.add(UNNotificationRequest(identifier: item.id, content: content, trigger: trigger))
        }
    }
}
