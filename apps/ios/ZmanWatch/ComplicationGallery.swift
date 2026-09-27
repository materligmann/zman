#if DEBUG
import SwiftUI
import WidgetKit
import ZmanCore

/// Les complications hors du cadran, pour les vérifier et pour les captures
/// des stores : lancer l'app montre avec l'argument -widgetGallery.
struct ComplicationGallery: View {
    let clock: ZmanClock

    var body: some View {
        TimelineView(.periodic(from: .now, by: 60)) { context in
            let entry = ZmanEntry(at: context.date, clock: clock)
            VStack(spacing: 10) {
                ZmanWidgetView(entry: entry, family: .accessoryRectangular)
                    .frame(height: 64)
                ZmanWidgetView(entry: entry, family: .accessoryInline)
                    .font(.system(size: 15))
                    .lineLimit(1)
            }
            .foregroundStyle(Theme.ink)
            .padding(.horizontal, 8)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.black.ignoresSafeArea())
        }
    }
}
#endif
