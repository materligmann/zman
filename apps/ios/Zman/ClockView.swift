import SwiftUI
import ZmanCore

/// L'horloge, comme la page d'accueil du site : date hébraïque, heure ·
/// chelek · rega, compteur absolu, qualité de la mesure. Aucune date
/// grégorienne ni heure civile.
struct ClockView: View {
    let model: ClockModel
    let settings: Settings
    let lang: Lang

    private var t: Strings { .of(lang) }
    private var tr: Tr { Tr(lang) }

    var body: some View {
        NavigationStack {
            clock
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: Page.self) { ReaderView(page: $0, lang: lang) }
        }
    }

    private var clock: some View {
        ScrollView {
            TimelineView(.animation(minimumInterval: 0.04)) { context in
                let m = model.clock.moment(at: context.date)
                VStack(spacing: 0) {
                    dateBlock(m)
                        .padding(.bottom, 28)
                    timeRow(m)
                    total(m)
                        .padding(.top, 36)
                    quality
                        .padding(.top, 26)
                    today(m)
                        .padding(.top, 34)
                    NavigationLink(value: Page.manifeste) {
                        Text(t.why)
                            .font(Theme.text(lang, 17))
                            .foregroundStyle(Theme.ink)
                            .underline(color: Theme.accent)
                    }
                    .padding(.top, 30)
                    note
                        .padding(.top, 40)
                }
                .multilineTextAlignment(.center)
                .padding(.horizontal, 20)
                .padding(.vertical, 48)
                .frame(maxWidth: 640)
                .frame(maxWidth: .infinity)
            }
        }
        .background(Theme.paper.ignoresSafeArea())
        .foregroundStyle(Theme.ink)
        .tint(Theme.accent)
        .layoutDirection(for: lang)
    }

    @ViewBuilder private func dateBlock(_ m: Moment) -> some View {
        if let d = m.date {
            Text(Names.dateHe(d))
                .font(Theme.hebrew(46, .largeTitle, weight: 500))
                .environment(\.layoutDirection, .rightToLeft)
                .minimumScaleFactor(0.6)
                .lineLimit(1)
            Text(Names.dateTranslit(d, lang))
                .font(Theme.text(lang, 22, .title3))
                .foregroundStyle(Theme.ink2)
                .padding(.top, 4)
            HStack(spacing: 8) {
                if lang != .he {
                    SmallCaps(Names.weekday(m.weekday, lang), lang: lang, size: 18, style: .body, tracking: 1.8)
                    Text("·")
                }
                Text(Names.weekdayHe(m.weekday)).font(Theme.hebrew(18))
            }
            .foregroundStyle(Theme.accent)
            .padding(.top, 12)
        }
    }

    private func timeRow(_ m: Moment) -> some View {
        let ready = model.calibration != nil
        return HStack(alignment: .top, spacing: 4) {
            group(Names.pad(m.hour, 2), t.hour)
            separator
            group(Names.pad(m.chelek, 4), t.chelek)
            separator
            group(Names.pad(m.regaInChelek, 2), t.rega)
        }
        .foregroundStyle(ready ? Theme.ink : Theme.rule)
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityElement(children: .combine)
    }

    private func group(_ value: String, _ label: String) -> some View {
        VStack(spacing: 8) {
            Text(value)
                .font(Theme.serif(64, .largeTitle).monospacedDigit())
                .lineLimit(1)
                .minimumScaleFactor(0.5)
            SmallCaps(label, lang: lang, size: 15, tracking: 2)
                .foregroundStyle(Theme.ink2)
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    private var separator: some View {
        Text("·")
            .font(Theme.serif(64, .largeTitle))
            .foregroundStyle(Theme.rule)
            .lineLimit(1)
            .minimumScaleFactor(0.5)
    }

    private func total(_ m: Moment) -> some View {
        VStack(spacing: 3) {
            Text(Names.group(m.rega))
                .font(Theme.serif(19, weight: 500).monospacedDigit())
                .tracking(0.8)
                .environment(\.layoutDirection, .leftToRight)
            SmallCaps(t.regaimSinceEpoch, lang: lang, size: 15, tracking: 1.4)
                .foregroundStyle(Theme.ink2)
        }
    }

    private var quality: some View {
        HStack(alignment: .firstTextBaseline, spacing: 7) {
            Circle()
                .fill(model.offline || model.calibration == nil ? Theme.ink2 : Theme.accent)
                .frame(width: 8, height: 8)
                .alignmentGuide(.firstTextBaseline) { $0[.bottom] - 1 }
            Text(t.quality(model.calibration, offline: model.offline))
                .font(Theme.text(lang, 15, .footnote))
                .foregroundStyle(Theme.ink2)
        }
    }

    /// Ce que le jour porte (fêtes, Roch Hodech, omer), et la prochaine fête.
    private func today(_ m: Moment) -> some View {
        let obs = Moadim.on(m.day, israel: settings.israel)
        let next = m.date.flatMap { d in
            (Moadim.year(d.year, israel: settings.israel) + Moadim.year(d.year + 1, israel: settings.israel))
                .first { $0.start > m.day && $0.feast.kind != .roshChodesh && $0.feast.kind != .cholHamoed }
        }
        return VStack(spacing: 6) {
            ForEach(obs, id: \.self) { o in
                let y = Luach.date(ofDay: o.start)?.year ?? 0
                Text(lang == .he ? o.nameHe(year: y) : "\(o.name(lang, year: y)) · \(o.nameHe(year: y))")
                    .font(Theme.text(lang, 19, weight: 500))
                    .foregroundStyle(Theme.accent)
            }
            if let n = Moadim.omer(m.day) {
                Text(tr.omer(n)).font(Theme.text(lang, 16)).foregroundStyle(Theme.accent)
            }
            if let next, let y = Luach.date(ofDay: next.start)?.year {
                Text(tr("\(next.name(lang, year: y)), \(tr.days(next.start - m.day))",
                        "\(next.name(lang, year: y)), \(tr.days(next.start - m.day))",
                        "\(next.nameHe(year: y)), \(tr.days(next.start - m.day))"))
                    .font(Theme.text(lang, 16))
                    .foregroundStyle(Theme.ink2)
            }
        }
    }

    private var note: some View {
        VStack(spacing: 16) {
            Rectangle().fill(Theme.rule).frame(height: 1)
            Text(t.note)
                .font(lang == .he ? Theme.hebrew(15, .footnote) : Theme.serif(15, .footnote, italic: true))
                .foregroundStyle(Theme.ink2)
            Link(t.privacy, destination: lang.siteURL("confidentialite"))
                .font(Theme.text(lang, 14, .footnote))
                .foregroundStyle(Theme.ink2)
                .underline()
                .padding(.top, 4)
        }
        .frame(maxWidth: 520)
    }
}
