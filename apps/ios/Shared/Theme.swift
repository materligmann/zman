import SwiftUI
import UIKit
import ZmanCore

// Encre, papier, un accent : les couleurs de web/static/css/site.css.
// Sur la montre, toujours sombre, le papier est noir (écran OLED).
enum Theme {
    #if os(watchOS)
    static let paper = Color.black
    #else
    static let paper = dynamic(0xf7f3ea, 0x16140f)
    #endif
    static let paper2 = dynamic(0xefe9dc, 0x1f1c15)
    static let ink = dynamic(0x1d1a15, 0xe9e2d3)
    static let ink2 = dynamic(0x5a544a, 0xa89f8c)
    static let rule = dynamic(0xd8d0bf, 0x3a3428)
    static let accent = dynamic(0x8d2b1c, 0xd8785c)

    // Polices du site, sous-ensembles latin (EB Garamond) et hébreu (Frank
    // Ruhl Libre) : chacune cascade vers l'autre, comme la pile CSS.
    private static let garamond = "EBGaramond-Regular"
    private static let garamondItalic = "EBGaramond-Italic"
    private static let frankRuhl = "FrankRuhlLibre-Regular"

    static func serif(_ size: CGFloat, _ style: UIFont.TextStyle = .body, weight: CGFloat = 400, italic: Bool = false) -> Font {
        font(italic ? garamondItalic : garamond, fallback: frankRuhl, size, style, weight)
    }

    static func hebrew(_ size: CGFloat, _ style: UIFont.TextStyle = .body, weight: CGFloat = 400) -> Font {
        font(frankRuhl, fallback: garamond, size, style, weight)
    }

    /// Police du texte courant selon la langue.
    static func text(_ lang: Lang, _ size: CGFloat, _ style: UIFont.TextStyle = .body, weight: CGFloat = 400) -> Font {
        lang == .he ? hebrew(size, style, weight: weight) : serif(size, style, weight: weight)
    }

    private static func font(_ name: String, fallback: String, _ size: CGFloat, _ style: UIFont.TextStyle, _ weight: CGFloat) -> Font {
        let wght = 0x7767_6874 // 'wght'
        let variation = [kCTFontVariationAttribute as UIFontDescriptor.AttributeName: [wght: weight]]
        let fallbackDesc = UIFontDescriptor(fontAttributes: [.name: fallback]).addingAttributes(variation)
        let desc = UIFontDescriptor(fontAttributes: [.name: name])
            .addingAttributes(variation)
            .addingAttributes([.cascadeList: [fallbackDesc]])
        return Font(UIFontMetrics(forTextStyle: style).scaledFont(for: UIFont(descriptor: desc, size: size)))
    }

    private static func dynamic(_ light: UInt32, _ dark: UInt32) -> Color {
        #if os(watchOS)
        Color(rgb(dark))
        #else
        Color(UIColor { $0.userInterfaceStyle == .dark ? rgb(dark) : rgb(light) })
        #endif
    }

    private static func rgb(_ v: UInt32) -> UIColor {
        UIColor(red: CGFloat((v >> 16) & 0xff) / 255, green: CGFloat((v >> 8) & 0xff) / 255,
                blue: CGFloat(v & 0xff) / 255, alpha: 1)
    }
}

/// Petites capitales synthétisées (les sous-ensembles n'ont pas `smcp`) ;
/// l'hébreu n'a pas de casse et reste tel quel.
struct SmallCaps: View {
    let text: String
    let lang: Lang
    let size: CGFloat
    var style: UIFont.TextStyle = .caption1
    var tracking: CGFloat = 1.2

    init(_ text: String, lang: Lang, size: CGFloat, style: UIFont.TextStyle = .caption1, tracking: CGFloat = 1.2) {
        self.text = text; self.lang = lang; self.size = size; self.style = style; self.tracking = tracking
    }

    var body: some View {
        if lang == .he || text.unicodeScalars.first.map({ (0x0590...0x05FF).contains($0.value) }) == true {
            Text(text).font(Theme.hebrew(size * 1.05, style))
        } else {
            Text(text.uppercased()).font(Theme.serif(size * 0.78, style, weight: 500)).tracking(tracking)
        }
    }
}

extension View {
    /// Mise en page de droite à gauche en hébreu.
    func layoutDirection(for lang: Lang) -> some View {
        environment(\.layoutDirection, lang.isRTL ? .rightToLeft : .leftToRight)
    }
}
