import SwiftUI
import ZmanCore

@main
struct ZmanApp: App {
    @State private var model = ClockModel()
    @State private var settings = Settings()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            root
                .task(id: scenePhase) {
                    // Au premier plan : synchroniser tout de suite, puis chaque minute.
                    guard scenePhase == .active else { return }
                    // Les rappels ne couvrent que les 60 prochains : les renouveler à chaque retour.
                    if settings.anyReminder {
                        await Reminders.reschedule(settings: settings, clock: model.clock, lang: .current)
                    }
                    while !Task.isCancelled {
                        await model.sync()
                        try? await Task.sleep(for: .seconds(60))
                    }
                }
        }
    }

    @ViewBuilder private var root: some View {
        #if DEBUG
        let args = CommandLine.arguments
        if args.contains("-widgetGallery") {
            WidgetGallery(clock: model.clock)
        } else if let i = args.firstIndex(of: "-page"), i + 1 < args.count, let page = Page(rawValue: args[i + 1]) {
            // Un texte ouvert directement, pour vérifier le rendu.
            NavigationStack { ReaderView(page: page, lang: .current) }
        } else {
            RootView(model: model, settings: settings, lang: .current)
        }
        #else
        RootView(model: model, settings: settings, lang: .current)
        #endif
    }
}

/// Cinq onglets : l'horloge, le luach, les fêtes, le molad, et le reste.
struct RootView: View {
    let model: ClockModel
    let settings: Settings
    let lang: Lang

    @State private var tab = RootView.initialTab

    var body: some View {
        let tr = Tr(lang)
        TabView(selection: $tab) {
            ClockView(model: model, settings: settings, lang: lang)
                .tabItem { Label(tr("Horloge", "Clock", "שעון"), systemImage: "clock") }
                .tag(0)
            LuachView(model: model, settings: settings, lang: lang)
                .tabItem { Label(tr("Luach", "Luach", "לוח"), systemImage: "calendar") }
                .tag(1)
            MoadimView(model: model, settings: settings, lang: lang)
                .tabItem { Label(tr("Fêtes", "Festivals", "מועדים"), systemImage: "sparkles") }
                .tag(2)
            MoladView(model: model, lang: lang)
                .tabItem { Label(tr("Molad", "Molad", "מולד"), systemImage: "moon") }
                .tag(3)
            MoreView(model: model, settings: settings, lang: lang)
                .tabItem { Label(tr("Plus", "More", "עוד"), systemImage: "book") }
                .tag(4)
        }
        .tint(Theme.accent)
        .layoutDirection(for: lang)
    }

    /// Onglet de départ ; `-tab n` au lancement, pour les captures des stores.
    static var initialTab: Int {
        let args = CommandLine.arguments
        if let i = args.firstIndex(of: "-tab"), i + 1 < args.count, let n = Int(args[i + 1]) { return n }
        return 0
    }
}
