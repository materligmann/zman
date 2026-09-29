import SwiftUI
import ZmanCore

/// Le luach : un mois hébreu en grille, de dimanche à Chabbat, avec les fêtes,
/// Roch Hodech et le molad du mois. Aucune date grégorienne.
struct LuachView: View {
    let model: ClockModel
    let settings: Settings
    let lang: Lang

    @State private var year: Int64 = 0
    @State private var month: Int = 0
    @State private var selected: Int64?

    private var tr: Tr { Tr(lang) }

    var body: some View {
        NavigationStack {
            TimelineView(.everyMinute) { context in
                let today = model.clock.moment(at: context.date).day
                ScrollView {
                    VStack(spacing: 0) {
                        if year > 0 {
                            header
                            grid(today: today)
                                .padding(.top, 18)
                            detail(selected ?? today, today: today)
                                .padding(.top, 22)
                            monthSummary
                                .padding(.top, 26)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 20)
                    .frame(maxWidth: 560)
                    .frame(maxWidth: .infinity)
                }
                .onAppear { if year == 0 { go(to: today) } }
            }
            .background(Theme.paper.ignoresSafeArea())
            .foregroundStyle(Theme.ink)
            .navigationTitle(tr("Luach", "Luach", "לוח"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(tr("Aujourd’hui", "Today", "היום")) { go(to: model.clock.moment().day) }
                }
            }
        }
    }

    private func go(to day: Int64) {
        guard let d = Luach.date(ofDay: day) else { return }
        year = d.year; month = d.month; selected = nil
    }

    /// Mois précédent ou suivant, dans l'ordre de l'année (Tichri → Eloul).
    private func step(_ delta: Int) {
        var y = year
        var order = Luach.monthsInOrder(y)
        var i = (order.firstIndex(of: month) ?? 0) + delta
        if i < 0 { y -= 1; order = Luach.monthsInOrder(y); i = order.count - 1 }
        if i >= order.count { y += 1; order = Luach.monthsInOrder(y); i = 0 }
        guard y >= 1 else { return }
        year = y; month = order[i]; selected = nil
    }

    private var first: HebrewDate { HebrewDate(year: year, month: month, day: 1) }
    private var firstDay: Int64 { Luach.day(of: first)! }
    private var length: Int { Luach.monthLength(year, month) }
    private var observances: [Observance] {
        Moadim.year(year, israel: settings.israel).filter { $0.end > firstDay && $0.start < firstDay + Int64(length) }
    }

    private var header: some View {
        HStack {
            Button { step(-1) } label: { Image(systemName: "chevron.backward").padding(10) }
                .accessibilityLabel(tr("Mois précédent", "Previous month", "החודש הקודם"))
            Spacer()
            VStack(spacing: 2) {
                Text("\(Names.monthHe(first)) \(Names.gematria(year))")
                    .font(Theme.hebrew(30, .title1, weight: 500))
                    .environment(\.layoutDirection, .rightToLeft)
                Text("\(Names.month(first, lang)) \(year)")
                    .font(Theme.text(lang, 17))
                    .foregroundStyle(Theme.ink2)
            }
            Spacer()
            Button { step(1) } label: { Image(systemName: "chevron.forward").padding(10) }
                .accessibilityLabel(tr("Mois suivant", "Next month", "החודש הבא"))
        }
        .tint(Theme.accent)
    }

    private func grid(today: Int64) -> some View {
        let lead = Luach.weekday(firstDay)
        let cols = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)
        let obs = observances
        return LazyVGrid(columns: cols, spacing: 4) {
            // Un seul ForEach aux identifiants distincts : en-têtes, cases vides, jours.
            ForEach(slots(lead: lead), id: \.self) { slot in
                if slot < 7 {
                    Text(tr.weekdayInitials[slot])
                        .font(Theme.text(lang, 13, .caption1))
                        .foregroundStyle(slot == Luach.shabbat ? Theme.accent : Theme.ink2)
                        .padding(.bottom, 4)
                } else if slot < 100 {
                    Color.clear.frame(height: 1)
                } else {
                    let i = slot - 100
                    let day = firstDay + Int64(i)
                    cell(day: day, n: i + 1, today: today, obs: obs.filter { $0.contains(day) })
                }
            }
        }
    }

    /// 0–6 : en-têtes ; 7… : cases vides avant le 1er ; 100 + i : jour i + 1.
    private func slots(lead: Int) -> [Int] {
        Array(0..<7) + (0..<lead).map { 7 + $0 } + (0..<length).map { 100 + $0 }
    }

    private func cell(day: Int64, n: Int, today: Int64, obs: [Observance]) -> some View {
        let isToday = day == today
        let isSelected = day == selected
        let main = obs.first { $0.feast.kind != .roshChodesh } ?? obs.first
        let wd = Luach.weekday(day)
        return Button { selected = day } label: {
            VStack(spacing: 1) {
                Text(Names.gematria(Int64(n)))
                    .font(Theme.hebrew(19, weight: isToday ? 600 : 400))
                Text("\(n)")
                    .font(Theme.serif(11, .caption2).monospacedDigit())
                    .foregroundStyle(Theme.ink2)
                Circle()
                    .fill(main.map { color($0.feast.kind) } ?? .clear)
                    .frame(width: 5, height: 5)
            }
            .frame(maxWidth: .infinity, minHeight: 54)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(isSelected ? Theme.accent.opacity(0.14) : (wd == Luach.shabbat ? Theme.paper2 : .clear))
            )
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(isToday ? Theme.accent : .clear, lineWidth: 1.5))
            .foregroundStyle(main?.feast.kind == .yomTov ? Theme.accent : Theme.ink)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(Names.dateTranslit(Luach.date(ofDay: day)!, lang)), \(Names.weekday(wd, lang))"
                            + obs.map { ", " + $0.name(lang, year: year) }.joined())
    }

    private func color(_ k: Feast.Kind) -> Color {
        switch k {
        case .yomTov: return Theme.accent
        case .cholHamoed, .minor, .modern: return Theme.accent.opacity(0.5)
        case .fast: return Theme.ink2
        case .roshChodesh: return Theme.ink
        }
    }

    /// Le jour choisi (aujourd'hui par défaut) : date, fêtes, omer, distance.
    private func detail(_ day: Int64, today: Int64) -> some View {
        let d = Luach.date(ofDay: day)!
        let wd = Luach.weekday(day)
        let obs = Moadim.on(day, israel: settings.israel)
        return VStack(spacing: 6) {
            Text(Names.dateHe(d))
                .font(Theme.hebrew(24, .title2, weight: 500))
                .environment(\.layoutDirection, .rightToLeft)
            Text("\(Names.weekday(wd, lang)) \(Names.dateTranslit(d, lang))")
                .font(Theme.text(lang, 16))
                .foregroundStyle(Theme.ink2)
            ForEach(obs, id: \.self) { o in
                let n = day - o.start + 1
                Text(o.length > 1 ? "\(o.name(lang, year: d.year)) · \(tr("jour", "day", "יום")) \(n)" : o.name(lang, year: d.year))
                    .font(Theme.text(lang, 17, weight: 500))
                    .foregroundStyle(Theme.accent)
            }
            if let n = Moadim.omer(day) {
                Text(tr.omer(n)).font(Theme.text(lang, 15)).foregroundStyle(Theme.accent)
            }
            SmallCaps(tr.days(day - today), lang: lang, size: 14, tracking: 1.4)
                .foregroundStyle(Theme.ink2)
                .padding(.top, 2)
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(16)
        .background(Theme.paper2, in: RoundedRectangle(cornerRadius: 12))
    }

    /// Le molad et les fêtes du mois.
    private var monthSummary: some View {
        VStack(alignment: .leading, spacing: 12) {
            SmallCaps(tr("Ce mois", "This month", "החודש"), lang: lang, size: 15, tracking: 1.6)
                .foregroundStyle(Theme.accent)
            row(tr("Longueur", "Length", "אורך"),
                length == 30 ? tr("30 jours (plein)", "30 days (full)", "30 ימים (מלא)") : tr("29 jours (défectif)", "29 days (defective)", "29 ימים (חסר)"))
            if let r = Luach.molad(year: year, month: month) {
                row(tr("Molad", "Molad", "מולד"), tr.announcement(MoladAnnouncement(rega: r)))
            }
            ForEach(observances, id: \.self) { o in
                row(o.name(lang, year: year), tr.join(tr.range(o), Tr.shortWeekday(Luach.weekday(o.start), lang)))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func row(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(Theme.text(lang, 16, weight: 500))
            Text(value).font(Theme.text(lang, 15)).foregroundStyle(Theme.ink2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, 8)
        .overlay(alignment: .bottom) { Rectangle().fill(Theme.rule).frame(height: 1) }
    }
}

extension Tr {
    static func shortWeekday(_ wd: Int, _ lang: Lang) -> String {
        lang == .he ? Names.weekdayHe(wd) : Names.weekday(wd, lang)
    }
}
