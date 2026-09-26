import SwiftUI
import WidgetKit
import ZmanCore

// Le widget ne peut pas compter les rega'im en direct : il affiche la date
// et l'heure du jour, avec une entrée de timeline à chaque début d'heure,
// et une barre de progression de l'heure que le système anime seul.
// Les vues sont dans Shared/WidgetViews.swift.

struct Provider: TimelineProvider {
    private let store = CalibrationStore()

    func placeholder(in context: Context) -> ZmanEntry {
        entry(at: Date(), clock: ZmanClock(calibration: nil))
    }

    func getSnapshot(in context: Context, completion: @escaping (ZmanEntry) -> Void) {
        completion(entry(at: Date(), clock: ZmanClock(calibration: store.load())))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<ZmanEntry>) -> Void) {
        Task {
            // Recaler sur le serveur si possible ; sinon, dernière correction connue.
            var calibration = store.load()
            if let c = try? await ZmanAPI.calibrate(timeout: 5) {
                calibration = c
                store.save(c)
            }
            let clock = ZmanClock(calibration: calibration)
            let now = Date()
            var entries = [entry(at: now, clock: clock)]
            // Une entrée par début d'heure sur les prochaines 24 heures (le
            // changement de jour, à la 0e heure, en fait partie).
            var next = clock.moment(at: now).hourEnd
            for _ in 0..<24 {
                entries.append(entry(at: clock.date(atRega: next), clock: clock))
                next += Rega.perHour
            }
            completion(Timeline(entries: entries, policy: .after(now.addingTimeInterval(3 * 3600))))
        }
    }

    private func entry(at date: Date, clock: ZmanClock) -> ZmanEntry {
        ZmanEntry(at: date, clock: clock)
    }
}

struct ZmanWidgetEntryView: View {
    @Environment(\.widgetFamily) private var family
    let entry: ZmanEntry

    var body: some View { ZmanWidgetView(entry: entry, family: family) }
}

struct ZmanWidget: Widget {
    var body: some WidgetConfiguration {
        let t = Strings.of(.current)
        return StaticConfiguration(kind: "ZmanWidget", provider: Provider()) { entry in
            ZmanWidgetEntryView(entry: entry)
        }
        .configurationDisplayName(t.widgetName)
        .description(t.widgetDescription)
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryCircular, .accessoryInline])
    }
}

@main
struct ZmanWidgetBundle: WidgetBundle {
    var body: some Widget { ZmanWidget() }
}

#Preview(as: .systemSmall) {
    ZmanWidget()
} timeline: {
    let clock = ZmanClock(calibration: nil)
    let m = Moment(rega: 4163021922875)
    ZmanEntry(date: .now, moment: m, hourStart: clock.date(atRega: m.hourStart), hourEnd: clock.date(atRega: m.hourEnd))
}
