import SwiftUI
import ZmanCore

/// L'horloge au poignet : date hébraïque, heure · chelek · rega, qualité de
/// la mesure. En mode toujours actif, l'écran n'est redessiné qu'une fois par
/// minute : le rega disparaît.
struct WatchClockView: View {
    let model: ClockModel
    let lang: Lang
    @Environment(\.isLuminanceReduced) private var dimmed

    private var t: Strings { .of(lang) }

    var body: some View {
        ScrollView {
            TimelineView(.animation(minimumInterval: 0.04, paused: dimmed)) { context in
                let m = model.clock.moment(at: context.date)
                VStack(spacing: 0) {
                    dateBlock(m)
                    timeRow(m)
                        .padding(.top, 10)
                    quality
                        .padding(.top, 12)
                }
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
            }
        }
        .background(Theme.paper.ignoresSafeArea())
        .foregroundStyle(Theme.ink)
        .layoutDirection(for: lang)
    }

    @ViewBuilder private func dateBlock(_ m: Moment) -> some View {
        if let d = m.date {
            Text(Names.dayMonthHe(d))
                .font(Theme.hebrew(26, .title2, weight: 500))
                .minimumScaleFactor(0.6)
                .lineLimit(1)
            Text(Names.gematria(d.year))
                .font(Theme.hebrew(16))
                .foregroundStyle(Theme.ink2)
            if lang == .he {
                Text(Names.weekdayHe(m.weekday))
                    .font(Theme.hebrew(15))
                    .foregroundStyle(Theme.accent)
                    .padding(.top, 2)
            } else {
                Text(Names.dateTranslit(d, lang))
                    .font(Theme.serif(15))
                    .foregroundStyle(Theme.ink2)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                SmallCaps(Names.weekday(m.weekday, lang), lang: lang, size: 14, tracking: 1.4)
                    .foregroundStyle(Theme.accent)
                    .padding(.top, 2)
            }
        }
    }

    private func timeRow(_ m: Moment) -> some View {
        let ready = model.calibration != nil
        return HStack(alignment: .top, spacing: 2) {
            group(Names.pad(m.hour, 2), t.hour)
            separator
            group(Names.pad(m.chelek, 4), t.chelek)
            if !dimmed {
                separator
                group(Names.pad(m.regaInChelek, 2), t.rega)
            }
        }
        .foregroundStyle(ready ? Theme.ink : Theme.rule)
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityElement(children: .combine)
    }

    private func group(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(Theme.serif(30, .title1).monospacedDigit())
                .lineLimit(1)
                .minimumScaleFactor(0.5)
            SmallCaps(label, lang: lang, size: 11, tracking: 1)
                .foregroundStyle(Theme.ink2)
                .lineLimit(1)
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    private var separator: some View {
        Text("·")
            .font(Theme.serif(30, .title1))
            .foregroundStyle(Theme.rule)
            .lineLimit(1)
    }

    private var quality: some View {
        HStack(alignment: .firstTextBaseline, spacing: 5) {
            Circle()
                .fill(model.offline || model.calibration == nil ? Theme.ink2 : Theme.accent)
                .frame(width: 6, height: 6)
                .alignmentGuide(.firstTextBaseline) { $0[.bottom] - 1 }
            Text(t.quality(model.calibration, offline: model.offline))
                .font(Theme.text(lang, 13, .footnote))
                .foregroundStyle(Theme.ink2)
        }
        .padding(.horizontal, 4)
    }
}
