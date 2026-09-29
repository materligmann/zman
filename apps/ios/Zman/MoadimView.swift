import SwiftUI
import ZmanCore

/// Les fêtes et les jeûnes d'une année, et le compte à rebours jusqu'à la
/// prochaine, en jours, heures et chalakim.
struct MoadimView: View {
    let model: ClockModel
    let settings: Settings
    let lang: Lang

    @State private var year: Int64 = 0

    private var tr: Tr { Tr(lang) }

    var body: some View {
        NavigationStack {
            TimelineView(.periodic(from: .now, by: 1)) { context in
                let now = model.clock.rega(at: context.date)
                let today = Rega.day(now)
                let list = year > 0 ? Moadim.year(year, israel: settings.israel).filter { $0.feast != .roshChodesh } : []
                ScrollView {
                    VStack(spacing: 0) {
                        if let next = nextFeast(after: today) {
                            countdown(next, now: now)
                                .padding(.bottom, 26)
                        }
                        yearPicker
                            .padding(.bottom, 10)
                        ForEach(list, id: \.self) { o in
                            row(o, today: today)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 20)
                    .frame(maxWidth: 560)
                    .frame(maxWidth: .infinity)
                }
                .onAppear { if year == 0 { year = Luach.date(ofDay: today)?.year ?? 5786 } }
            }
            .background(Theme.paper.ignoresSafeArea())
            .foregroundStyle(Theme.ink)
            .navigationTitle(tr("Fêtes", "Festivals", "מועדים"))
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    /// La prochaine fête (hors Roch Hodech, hol hamoed compris seulement s'il ouvre la liste).
    private func nextFeast(after today: Int64) -> Observance? {
        guard let y = Luach.date(ofDay: today)?.year else { return nil }
        return (Moadim.year(y, israel: settings.israel) + Moadim.year(y + 1, israel: settings.israel))
            .first { $0.start > today && $0.feast.kind != .roshChodesh && $0.feast.kind != .cholHamoed }
    }

    private func countdown(_ o: Observance, now: Int64) -> some View {
        let y = Luach.date(ofDay: o.start)!.year
        return VStack(spacing: 8) {
            SmallCaps(tr("Prochaine fête", "Next festival", "המועד הבא"), lang: lang, size: 15, tracking: 1.6)
                .foregroundStyle(Theme.accent)
            Text(o.nameHe(year: y))
                .font(Theme.hebrew(34, .largeTitle, weight: 500))
            if lang != .he {
                Text(o.name(lang, year: y)).font(Theme.text(lang, 20, .title3)).foregroundStyle(Theme.ink2)
            }
            Text(tr.span(Rega.dayStart(o.start) - now))
                .font(Theme.serif(26, .title2).monospacedDigit())
                .padding(.top, 4)
            Text(tr.join(Tr.shortWeekday(Luach.weekday(o.start), lang), tr.range(o)))
                .font(Theme.text(lang, 15))
                .foregroundStyle(Theme.ink2)
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(20)
        .background(Theme.paper2, in: RoundedRectangle(cornerRadius: 14))
        .accessibilityElement(children: .combine)
    }

    private var yearPicker: some View {
        HStack {
            Button { year -= 1 } label: { Image(systemName: "chevron.backward").padding(10) }
                .accessibilityLabel(tr("Année précédente", "Previous year", "השנה הקודמת"))
            Spacer()
            VStack(spacing: 0) {
                Text(Names.gematria(year)).font(Theme.hebrew(24, .title2, weight: 500))
                Text(String(year)).font(Theme.serif(15).monospacedDigit()).foregroundStyle(Theme.ink2)
            }
            Spacer()
            Button { year += 1 } label: { Image(systemName: "chevron.forward").padding(10) }
                .accessibilityLabel(tr("Année suivante", "Next year", "השנה הבאה"))
        }
        .tint(Theme.accent)
    }

    private func row(_ o: Observance, today: Int64) -> some View {
        let past = o.end <= today
        let current = o.contains(today)
        return HStack(alignment: .firstTextBaseline, spacing: 12) {
            Circle().fill(dot(o.feast.kind)).frame(width: 7, height: 7)
            VStack(alignment: .leading, spacing: 2) {
                Text(o.name(lang, year: year))
                    .font(Theme.text(lang, 17, weight: o.feast.kind == .yomTov ? 500 : 400))
                if lang != .he {
                    Text(o.nameHe(year: year)).font(Theme.hebrew(15)).foregroundStyle(Theme.ink2)
                }
                Text(tr.join(Tr.shortWeekday(Luach.weekday(o.start), lang), tr.range(o), o.length > 1 ? tr.dayCount(o.length) : ""))
                    .font(Theme.text(lang, 14, .footnote))
                    .foregroundStyle(Theme.ink2)
            }
            Spacer(minLength: 8)
            Text(current ? tr("en cours", "now", "עכשיו") : tr.days(o.start - today))
                .font(Theme.text(lang, 14, .footnote))
                .foregroundStyle(current ? Theme.accent : Theme.ink2)
        }
        .padding(.vertical, 10)
        .overlay(alignment: .bottom) { Rectangle().fill(Theme.rule).frame(height: 1) }
        .opacity(past ? 0.5 : 1)
        .accessibilityElement(children: .combine)
    }

    private func dot(_ k: Feast.Kind) -> Color {
        switch k {
        case .yomTov: return Theme.accent
        case .fast: return Theme.ink2
        default: return Theme.accent.opacity(0.45)
        }
    }
}
