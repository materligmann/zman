import SwiftUI
import ZmanCore

@main
struct ZmanApp: App {
    @State private var model = ClockModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            root
                .task(id: scenePhase) {
                    // Au premier plan : synchroniser tout de suite, puis chaque minute.
                    guard scenePhase == .active else { return }
                    while !Task.isCancelled {
                        await model.sync()
                        try? await Task.sleep(for: .seconds(60))
                    }
                }
        }
    }

    @ViewBuilder private var root: some View {
        #if DEBUG
        if CommandLine.arguments.contains("-widgetGallery") {
            WidgetGallery(clock: model.clock)
        } else {
            ClockView(model: model, lang: .current)
        }
        #else
        ClockView(model: model, lang: .current)
        #endif
    }
}
