import SwiftUI
import ZmanCore

/// Les pages du site, lues dans l'app : blocs JSON produits par
/// scripts/gen-app-texts.py à partir de web/content.
enum Page: String, CaseIterable, Identifiable, Hashable {
    case manifeste, methode, api, confidentialite

    var id: String { rawValue }

    func title(_ lang: Lang) -> String {
        let tr = Tr(lang)
        switch self {
        case .manifeste: return tr("Manifeste", "Manifesto", "מניפסט")
        case .methode: return tr("Méthode", "Method", "שיטה")
        case .api: return tr("L’API", "The API", "ה־API")
        case .confidentialite: return tr("Confidentialité", "Privacy", "פרטיות")
        }
    }

    func summary(_ lang: Lang) -> String {
        let tr = Tr(lang)
        switch self {
        case .manifeste: return tr("Un temps de rotation, pas un temps d’atome", "Rotation time, not atomic time", "זמן של סיבוב, לא זמן של אטום")
        case .methode: return tr("Le calcul, ce qui est vérifié, ce qui reste à faire", "The computation, what is tested, what remains", "החישוב, מה נבדק ומה נותר")
        case .api: return tr("Le timestamp juif, pour les développeurs", "The Jewish timestamp, for developers", "חותמת הזמן היהודית, למפתחים")
        case .confidentialite: return tr("Rien n’est collecté", "Nothing is collected", "דבר אינו נאסף")
        }
    }

    func blocks(_ lang: Lang) -> [Block] {
        guard let url = Bundle.main.url(forResource: "\(lang.rawValue)-\(rawValue)", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return [] }
        return (try? JSONDecoder().decode([Block].self, from: data)) ?? []
    }
}

struct Block: Decodable {
    let t: String
    var s: String?
    var items: [String]?
    var rows: [[String]]?
}

struct ReaderView: View {
    let page: Page
    let lang: Lang
    @State private var next: Page?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(page.blocks(lang).enumerated()), id: \.offset) { _, b in
                    block(b)
                }
            }
            .padding(.horizontal, 22)
            .padding(.vertical, 28)
            .frame(maxWidth: 680, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .background(Theme.paper.ignoresSafeArea())
        .foregroundStyle(Theme.ink)
        .navigationTitle(page.title(lang))
        .navigationBarTitleDisplayMode(.inline)
        .environment(\.openURL, OpenURLAction { url in
            if url.scheme == "zman", let p = Page(rawValue: url.host() ?? "") {
                next = p
                return .handled
            }
            return .systemAction
        })
        .navigationDestination(item: $next) { ReaderView(page: $0, lang: lang) }
    }

    @ViewBuilder private func block(_ b: Block) -> some View {
        switch b.t {
        case "h1":
            Text(inline(b.s)).font(Theme.text(lang, 32, .largeTitle, weight: 500))
                .padding(.bottom, 10)
        case "lede":
            Text(inline(b.s)).font(lang == .he ? Theme.hebrew(19, .title3) : Theme.serif(19, .title3, italic: true))
                .foregroundStyle(Theme.ink2)
                .padding(.bottom, 18)
        case "h2":
            VStack(alignment: .leading, spacing: 10) {
                Rectangle().fill(Theme.rule).frame(height: 1)
                Text(inline(b.s)).font(Theme.text(lang, 23, .title2, weight: 500))
            }
            .padding(.top, 22).padding(.bottom, 10)
        case "h3":
            Text(inline(b.s)).font(Theme.text(lang, 18, .headline, weight: 500))
                .foregroundStyle(Theme.accent)
                .padding(.top, 16).padding(.bottom, 6)
        case "p":
            paragraph(b.s)
        case "ul", "ol":
            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array((b.items ?? []).enumerated()), id: \.offset) { i, item in
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Text(b.t == "ol" ? "\(i + 1)." : "•")
                            .font(Theme.text(lang, 17))
                            .foregroundStyle(Theme.accent)
                        Text(inline(item)).font(Theme.text(lang, 17))
                    }
                }
            }
            .padding(.bottom, 14)
        case "pre":
            ScrollView(.horizontal, showsIndicators: false) {
                Text(b.s ?? "")
                    .font(.system(size: 12, design: .monospaced))
                    .padding(12)
            }
            .background(Theme.paper2, in: RoundedRectangle(cornerRadius: 6))
            .environment(\.layoutDirection, .leftToRight)
            .padding(.bottom, 14)
        case "table":
            table(b.rows ?? [])
        case "sources":
            VStack(alignment: .leading, spacing: 6) {
                Rectangle().fill(Theme.rule).frame(height: 1).padding(.top, 22)
                ForEach(Array((b.items ?? []).enumerated()), id: \.offset) { i, item in
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text("\(i + 1).").monospacedDigit().foregroundStyle(Theme.ink2)
                        Text(inline(item))
                    }
                    .font(Theme.text(lang, 14, .footnote))
                }
            }
        default:
            EmptyView()
        }
    }

    private func paragraph(_ s: String?) -> some View {
        Text(inline(s))
            .font(Theme.text(lang, 17))
            .lineSpacing(3)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.bottom, 14)
    }

    /// Un tableau du site, en fiches : sur un téléphone, trois colonnes ne tiennent pas.
    private func table(_ rows: [[String]]) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(rows.dropFirst().enumerated()), id: \.offset) { _, row in
                VStack(alignment: .leading, spacing: 3) {
                    Text(inline(row.first)).font(Theme.text(lang, 16, weight: 500))
                    if row.count > 1 {
                        Text(inline(row[1])).font(Theme.text(lang, 16)).foregroundStyle(Theme.accent)
                    }
                    if row.count > 2, !row[2].isEmpty {
                        Text(inline(row[2])).font(Theme.text(lang, 14, .footnote)).foregroundStyle(Theme.ink2)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 10)
                .overlay(alignment: .bottom) { Rectangle().fill(Theme.rule).frame(height: 1) }
            }
        }
        .padding(.bottom, 14)
    }

    private func inline(_ s: String?) -> AttributedString {
        let opts = AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        return (try? AttributedString(markdown: s ?? "", options: opts)) ?? AttributedString(s ?? "")
    }
}
