#if DEBUG
import SwiftUI
import WidgetKit
import ZmanCore

/// Galerie des tailles du widget, pour vérifier le rendu sans passer par
/// l'écran d'accueil : lancer l'app avec l'argument -widgetGallery.
struct WidgetGallery: View {
    let clock: ZmanClock

    var body: some View {
        TimelineView(.periodic(from: .now, by: 60)) { context in
            let entry = ZmanEntry(at: context.date, clock: clock)
            ScrollView {
                VStack(spacing: 24) {
                    // Le format rond de l'écran verrouillé n'est pas montré : hors
                    // widget, sa jauge minutée s'affiche comme une roue d'attente.
                    tile(entry, .systemSmall, CGSize(width: 170, height: 170))
                    tile(entry, .systemMedium, CGSize(width: 364, height: 170))
                    tile(entry, .accessoryRectangular, CGSize(width: 172, height: 76), lockScreen: true)
                    tile(entry, .accessoryInline, CGSize(width: 300, height: 24), lockScreen: true)
                }
                .padding(.vertical, 40)
                .frame(maxWidth: .infinity)
            }
            .background(
                // Un fond d'écran d'accueil, pour les captures des stores.
                LinearGradient(colors: [Color(red: 0.16, green: 0.20, blue: 0.33), Color(red: 0.42, green: 0.30, blue: 0.26)],
                               startPoint: .top, endPoint: .bottom)
                    .ignoresSafeArea()
            )
        }
    }

    private func tile(_ entry: ZmanEntry, _ family: WidgetFamily, _ size: CGSize, lockScreen: Bool = false) -> some View {
        ZmanWidgetView(entry: entry, family: family)
            .padding(lockScreen ? 0 : 16)
            .frame(width: size.width, height: size.height)
            .background(lockScreen ? AnyShapeStyle(.clear) : AnyShapeStyle(Theme.paper))
            .foregroundStyle(lockScreen ? .white : Theme.ink)
            .clipShape(RoundedRectangle(cornerRadius: lockScreen ? 12 : 22, style: .continuous))
    }
}
#endif
