import SwiftUI
import ZmanCore

/// Le molad : compte à rebours jusqu'au prochain, les moladot de l'année au
/// format des annonces, et la structure de l'année (keviah, cycle).
struct MoladView: View {
    let model: ClockModel
    let lang: Lang

    @State private var year: Int64 = 0

    private var tr: Tr { Tr(lang) }

    var body: some View {
        NavigationStack {
            TimelineView(.animation(minimumInterval: 0.1)) { context in
                let now = model.clock.rega(at: context.date)
                ScrollView {
                    VStack(spacing: 0) {
                        if let next = nextMolad(after: now) {
                            countdown(next, now: now)
                                .padding(.bottom, 28)
                        }
                        if year > 0 {
                            yearPicker
                            moladot(now: now)
                                .padding(.top, 8)
                            structure
                                .padding(.top, 28)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 20)
                    .frame(maxWidth: 560)
                    .frame(maxWidth: .infinity)
                }
                .onAppear { if year == 0 { year = Luach.date(ofDay: Rega.day(now))?.year ?? 5786 } }
            }
            .background(Theme.paper.ignoresSafeArea())
            .foregroundStyle(Theme.ink)
            .navigationTitle(tr("Molad", "Molad", "מולד"))
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func nextMolad(after now: Int64) -> (year: Int64, month: Int, rega: Int64)? {
        guard let y = Luach.date(ofDay: Rega.day(now))?.year else { return nil }
        for yy in [y, y + 1] {
            for m in Luach.monthsInOrder(yy) {
                if let r = Luach.molad(year: yy, month: m), r > now { return (yy, m, r) }
            }
        }
        return nil
    }

    private func countdown(_ m: (year: Int64, month: Int, rega: Int64), now: Int64) -> some View {
        let d = HebrewDate(year: m.year, month: m.month, day: 1)
        let left = m.rega - now
        let p = Rega.split(left)
        return VStack(spacing: 8) {
            SmallCaps(tr("Prochain molad", "Next molad", "המולד הבא"), lang: lang, size: 15, tracking: 1.6)
                .foregroundStyle(Theme.accent)
            Text("מולד \(Names.monthHe(d))")
                .font(Theme.hebrew(34, .largeTitle, weight: 500))
            if lang != .he {
                Text(tr("Molad de \(Names.month(d, lang))", "Molad of \(Names.month(d, lang))", ""))
                    .font(Theme.text(lang, 19, .title3)).foregroundStyle(Theme.ink2)
            }
            HStack(alignment: .top, spacing: 4) {
                unit(String(p.day), tr("jours", "days", "ימים"))
                sep
                unit(Names.pad(p.hour, 2), tr("heures", "hours", "שעות"))
                sep
                unit(Names.pad(p.chelek, 4), tr("chalakim", "chalakim", "חלקים"))
                sep
                unit(Names.pad(p.rega, 2), tr("rega’im", "rega’im", "רגעים"))
            }
            .environment(\.layoutDirection, .leftToRight)
            .padding(.top, 6)
            Text(tr.announcement(MoladAnnouncement(rega: m.rega)))
                .font(Theme.text(lang, 16))
                .foregroundStyle(Theme.ink2)
                .padding(.top, 4)
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(20)
        .background(Theme.paper2, in: RoundedRectangle(cornerRadius: 14))
        .accessibilityElement(children: .combine)
    }

    private func unit(_ v: String, _ label: String) -> some View {
        VStack(spacing: 4) {
            Text(v).font(Theme.serif(32, .title1).monospacedDigit()).lineLimit(1).minimumScaleFactor(0.5)
            SmallCaps(label, lang: lang, size: 12, tracking: 1.2).foregroundStyle(Theme.ink2)
        }
    }

    private var sep: some View {
        Text("·").font(Theme.serif(32, .title1)).foregroundStyle(Theme.rule)
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

    private func moladot(now: Int64) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            SmallCaps(tr("Les moladot de l’année", "This year’s moladot", "מולדות השנה"), lang: lang, size: 15, tracking: 1.6)
                .foregroundStyle(Theme.accent)
                .padding(.bottom, 6)
            ForEach(Luach.monthsInOrder(year), id: \.self) { m in
                let r = Luach.molad(year: year, month: m)!
                let p = Rega.split(r)
                let d = HebrewDate(year: year, month: m, day: 1)
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(Names.month(d, lang)).font(Theme.text(lang, 17, weight: 500))
                        Text(tr.announcement(MoladAnnouncement(rega: r)))
                            .font(Theme.text(lang, 14, .footnote)).foregroundStyle(Theme.ink2)
                    }
                    Spacer(minLength: 8)
                    // Notation du calendrier : jour de la semaine, heure, chalakim.
                    Text("\(Luach.weekday(p.day) + 1) · \(p.hour) · \(p.chelek)")
                        .font(Theme.serif(15).monospacedDigit())
                        .foregroundStyle(Theme.ink2)
                        .environment(\.layoutDirection, .leftToRight)
                }
                .padding(.vertical, 9)
                .overlay(alignment: .bottom) { Rectangle().fill(Theme.rule).frame(height: 1) }
                .opacity(r < now ? 0.5 : 1)
            }
            Text(tr("À droite : jour de la semaine (1 = dimanche), heure depuis 18:00 la veille, chalakim — la notation du Rambam.",
                    "Right: weekday (1 = Sunday), hour since 18:00 the evening before, chalakim — the Rambam’s notation.",
                    "משמאל: יום בשבוע (1 = ראשון), שעה מ־18:00 בערב שלפניו, חלקים — כדרך הרמב״ם."))
                .font(Theme.text(lang, 13, .caption1))
                .foregroundStyle(Theme.ink2)
                .padding(.top, 8)
        }
    }

    /// Longueur, caractère, keviah, cycle de dix-neuf ans, mois.
    private var structure: some View {
        let y = YearInfo(year)
        let kind: String
        switch y.kind {
        case .deficient: kind = tr("défective (חסרה)", "deficient (חסרה)", "חסרה")
        case .regular: kind = tr("régulière (כסדרה)", "regular (כסדרה)", "כסדרה")
        case .complete: kind = tr("complète (שלמה)", "complete (שלמה)", "שלמה")
        }
        return VStack(alignment: .leading, spacing: 0) {
            SmallCaps(tr("L’année", "The year", "השנה"), lang: lang, size: 15, tracking: 1.6)
                .foregroundStyle(Theme.accent)
                .padding(.bottom, 6)
            fact(tr("Longueur", "Length", "אורך"), "\(tr.dayCount(y.length)) · \(kind)")
            fact(tr("Type", "Type", "סוג"), y.isLeap
                 ? tr("embolismique : 13 mois, avec Adar I et Adar II", "leap: 13 months, with Adar I and Adar II", "מעוברת: 13 חודשים, אדר א׳ ואדר ב׳")
                 : tr("commune : 12 mois", "common: 12 months", "פשוטה: 12 חודשים"))
            fact(tr("Keviah", "Keviah", "קביעות"), "\(y.keviah) · " + tr(
                "Roch Hachana \(Names.weekday(Luach.weekday(y.roshHashana), .fr)), Pessah \(Names.weekday(Luach.weekday(y.pesach), .fr))",
                "Rosh Hashana on \(Names.weekday(Luach.weekday(y.roshHashana), .en)), Pesach on \(Names.weekday(Luach.weekday(y.pesach), .en))",
                "ראש השנה ב\(Names.weekdayHe(Luach.weekday(y.roshHashana))), פסח ב\(Names.weekdayHe(Luach.weekday(y.pesach)))"))
            fact(tr("Cycle de 19 ans", "19-year cycle", "מחזור קטן"), tr(
                "année \(y.yearInCycle) du cycle \(y.cycle)", "year \(y.yearInCycle) of cycle \(y.cycle)",
                "שנה \(y.yearInCycle) במחזור \(y.cycle)"))
            fact(tr("Mois", "Months", "חודשים"), y.months.map { m in
                "\(Names.month(HebrewDate(year: year, month: m.month, day: 1), lang)) \(m.length)"
            }.joined(separator: " · "))
        }
    }

    private func fact(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(Theme.text(lang, 16, weight: 500))
            Text(value).font(Theme.text(lang, 15)).foregroundStyle(Theme.ink2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
        .overlay(alignment: .bottom) { Rectangle().fill(Theme.rule).frame(height: 1) }
    }
}
