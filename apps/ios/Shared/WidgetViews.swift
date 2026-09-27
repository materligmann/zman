import SwiftUI
import WidgetKit
import ZmanCore

// Vues du widget, partagées avec l'app pour la galerie de prévisualisation
// (argument de lancement -widgetGallery, en debug).

struct ZmanEntry: TimelineEntry {
    let date: Date
    let moment: Moment
    let hourStart: Date
    let hourEnd: Date
}

extension ZmanEntry {
    init(at date: Date, clock: ZmanClock) {
        // Évaluer un peu après la frontière pour tomber du bon côté.
        let m = clock.moment(at: date.addingTimeInterval(0.05))
        self.init(date: date, moment: m,
                  hourStart: clock.date(atRega: m.hourStart),
                  hourEnd: clock.date(atRega: m.hourEnd))
    }
}

struct ZmanWidgetView: View {
    let entry: ZmanEntry
    let family: WidgetFamily
    let lang = Lang.current

    private var t: Strings { .of(lang) }
    private var m: Moment { entry.moment }

    var body: some View {
        Group {
            switch family {
            case .accessoryInline: inline
            case .accessoryCircular: circular
            case .accessoryRectangular: rectangular
            #if os(watchOS)
            case .accessoryCorner: corner
            default: circular
            #else
            case .systemMedium: medium
            default: small
            #endif
            }
        }
        .containerBackground(for: .widget) { Theme.paper }
        .layoutDirection(for: lang)
    }

    private var hourProgress: some View {
        ProgressView(timerInterval: entry.hourStart...entry.hourEnd, countsDown: false) {
            EmptyView()
        } currentValueLabel: {
            EmptyView()
        }
        .progressViewStyle(.linear)
        .tint(Theme.accent)
        // Sens de lecture de la langue, même dans le bloc hébreu.
        .environment(\.layoutDirection, lang.isRTL ? .rightToLeft : .leftToRight)
    }

    // Petit : jour et mois en grand, année, jour de la semaine, heure.
    private var small: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let d = m.date {
                // Jour de la semaine et translittération dans la langue de
                // l'appareil ; la date hébraïque reste l'élément central.
                if lang == .he {
                    Text(Names.weekdayHe(m.weekday))
                        .font(Theme.hebrew(14))
                        .foregroundStyle(Theme.accent)
                } else {
                    SmallCaps(Names.weekday(m.weekday, lang), lang: lang, size: 15)
                        .foregroundStyle(Theme.accent)
                }
                Spacer(minLength: 2)
                Text(Names.dayMonthHe(d))
                    .font(Theme.hebrew(30, weight: 500))
                    .minimumScaleFactor(0.6)
                    .lineLimit(1)
                if lang == .he {
                    Text(Names.gematria(d.year))
                        .font(Theme.hebrew(17))
                        .foregroundStyle(Theme.ink2)
                } else {
                    Text(Names.dateTranslit(d, lang))
                        .font(Theme.serif(15))
                        .foregroundStyle(Theme.ink2)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
            }
            Spacer(minLength: 6)
            SmallCaps(t.hourLabel(m.hour), lang: lang, size: 14)
                .foregroundStyle(Theme.ink2)
            hourProgress
                .padding(.top, 3)
        }
        .foregroundStyle(Theme.ink)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }

    // Moyen : la date hébraïque d'un côté, la translittération et l'heure de l'autre.
    private var medium: some View {
        HStack(spacing: 16) {
            VStack(spacing: 2) {
                if let d = m.date {
                    Text(Names.dayMonthHe(d))
                        .font(Theme.hebrew(34, weight: 500))
                        .minimumScaleFactor(0.6)
                        .lineLimit(1)
                    Text(Names.gematria(d.year))
                        .font(Theme.hebrew(20))
                        .foregroundStyle(Theme.ink2)
                    Text(Names.weekdayHe(m.weekday))
                        .font(Theme.hebrew(15))
                        .foregroundStyle(Theme.accent)
                        .padding(.top, 4)
                }
            }
            .frame(maxWidth: .infinity)
            .environment(\.layoutDirection, .rightToLeft)

            Rectangle().fill(Theme.rule).frame(width: 1)

            VStack(alignment: .leading, spacing: 4) {
                if let d = m.date, lang != .he {
                    Text(Names.dateTranslit(d, lang))
                        .font(Theme.serif(17))
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    SmallCaps(Names.weekday(m.weekday, lang), lang: lang, size: 16)
                        .foregroundStyle(Theme.accent)
                } else if let d = m.date {
                    Text(Names.dateTranslit(d, lang))
                        .font(Theme.hebrew(17))
                        .lineLimit(1)
                }
                Spacer(minLength: 4)
                SmallCaps(t.hourLabel(m.hour), lang: lang, size: 15)
                    .foregroundStyle(Theme.ink2)
                hourProgress
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
        .foregroundStyle(Theme.ink)
    }

    // Écran verrouillé.
    private var rectangular: some View {
        VStack(alignment: .leading, spacing: 1) {
            if let d = m.date {
                Text(Names.dateHe(d))
                    .font(Theme.hebrew(17, weight: 600))
                    .widgetAccentable()
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text((lang == .he ? Names.weekdayHe(m.weekday) : Names.weekday(m.weekday, lang)) + " · " + t.hourLabel(m.hour))
                    .font(Theme.text(lang, 13))
                    .lineLimit(1)
            }
            ProgressView(timerInterval: entry.hourStart...entry.hourEnd, countsDown: false) {
                EmptyView()
            } currentValueLabel: {
                EmptyView()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var circular: some View {
        ProgressView(timerInterval: entry.hourStart...entry.hourEnd, countsDown: false) {
            EmptyView()
        } currentValueLabel: {
            Text(String(m.hour))
                .font(Theme.serif(20, weight: 500))
        }
        .progressViewStyle(.circular)
        .widgetAccentable()
    }

    #if os(watchOS)
    // Coin du cadran : le numéro de l'heure, la part écoulée en arc.
    private var corner: some View {
        Text(String(m.hour))
            .font(Theme.serif(22, weight: 500))
            .widgetCurvesContent()
            .widgetLabel {
                ProgressView(timerInterval: entry.hourStart...entry.hourEnd, countsDown: false) {
                    EmptyView()
                } currentValueLabel: {
                    EmptyView()
                }
                .tint(Theme.accent)
            }
    }
    #endif

    private var inline: some View {
        Text(m.date.map { d in
            let day = lang == .he ? Names.dayMonthHe(d) : "\(d.day) \(Names.month(d, lang))"
            return day + " · " + t.hourLabel(m.hour)
        } ?? "")
    }
}
