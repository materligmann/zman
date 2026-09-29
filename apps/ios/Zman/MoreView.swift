import SwiftUI
import ZmanCore

/// Rappels, réglage Israël / diaspora, les textes du site, le widget et la montre.
struct MoreView: View {
    let model: ClockModel
    @Bindable var settings: Settings
    let lang: Lang

    @State private var denied = false

    private var tr: Tr { Tr(lang) }
    private var t: Strings { .of(lang) }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Group {
                    toggle(tr("Roch Hodech", "Rosh Chodesh", "ראש חודש"), $settings.remindRoshChodesh)
                    toggle(tr("Fêtes et jeûnes", "Festivals and fasts", "מועדים ותעניות"), $settings.remindFeasts)
                    toggle(tr("Molad", "Molad", "מולד"), $settings.remindMolad)
                    }
                    .listRowBackground(Theme.paper2)
                } header: {
                    header(tr("Rappels", "Reminders", "תזכורות"))
                } footer: {
                    Text(denied
                         ? tr("Les notifications sont refusées pour Zman : autorisez-les dans Réglages › Notifications › Zman.",
                              "Notifications are turned off for Zman: allow them in Settings › Notifications › Zman.",
                              "ההתראות של זמן כבויות: אפשרו אותן בהגדרות › התראות › Zman.")
                         : tr("Une notification à la 22ᵉ heure de la veille, deux heures avant que le jour commence ; pour le molad, à l’instant du molad. Tout est programmé sur l’appareil.",
                              "A notification at the 22nd hour of the day before, two hours before the day begins; for the molad, at the moment of the molad. Everything is scheduled on the device.",
                              "התראה בשעה ה־22 של היום הקודם, שעתיים לפני שהיום מתחיל; ובמולד, ברגע המולד. הכול מתוזמן במכשיר עצמו."))
                        .font(Theme.text(lang, 13, .footnote))
                }

                Section {
                    Group {
                    Toggle(isOn: $settings.israel) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(tr("En Israël", "In Israel", "בארץ ישראל")).font(Theme.text(lang, 17))
                            Text(tr("Un seul jour de fête (sans יום טוב שני)", "One festival day (no יום טוב שני)", "יום טוב אחד, ללא יום טוב שני של גלויות"))
                                .font(Theme.text(lang, 13, .footnote)).foregroundStyle(Theme.ink2)
                        }
                    }
                    }
                    .listRowBackground(Theme.paper2)
                } header: {
                    header(tr("Calendrier", "Calendar", "לוח"))
                }

                Section {
                    Group {
                    ForEach(Page.allCases) { page in
                        NavigationLink(value: page) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(page.title(lang)).font(Theme.text(lang, 17))
                                Text(page.summary(lang)).font(Theme.text(lang, 13, .footnote)).foregroundStyle(Theme.ink2)
                            }
                        }
                    }
                    }
                    .listRowBackground(Theme.paper2)
                } header: {
                    header(tr("Lire", "Read", "לקריאה"))
                }

                Section {
                    Group {
                    Text(t.widgetHint).font(Theme.text(lang, 15))
                    Text(tr("Sur l’Apple Watch, l’app Zman s’installe avec celle de l’iPhone ; ses complications s’ajoutent depuis l’édition du cadran.",
                            "On Apple Watch, the Zman app installs with the iPhone app; add its complications by editing the watch face.",
                            "ב־Apple Watch האפליקציה מותקנת יחד עם אפליקציית האייפון; את הסיבוכים מוסיפים בעריכת פני השעון."))
                        .font(Theme.text(lang, 15))
                    }
                    .listRowBackground(Theme.paper2)
                } header: {
                    header(tr("Widget et montre", "Widget and watch", "ווידג׳ט ושעון"))
                }

                Section {
                    Group {
                    Link(destination: lang.siteURL("")) {
                        Text("zman.technology").font(Theme.text(lang, 17))
                    }
                    Text(tr("Aucun compte, aucune publicité, aucun suivi. Code sous licence MIT, données de l’API sous CC BY 4.0.",
                            "No account, no ads, no tracking. Code under the MIT licence, API data under CC BY 4.0.",
                            "ללא חשבון, ללא פרסומות, ללא מעקב. הקוד ברישיון MIT, נתוני ה־API ברישיון CC BY 4.0."))
                        .font(Theme.text(lang, 13, .footnote)).foregroundStyle(Theme.ink2)
                    }
                    .listRowBackground(Theme.paper2)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.paper.ignoresSafeArea())
            .foregroundStyle(Theme.ink)
            .navigationTitle(tr("Plus", "More", "עוד"))
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: Page.self) { ReaderView(page: $0, lang: lang) }
        }
        .onChange(of: settings.remindRoshChodesh) { _, on in changed(on) }
        .onChange(of: settings.remindFeasts) { _, on in changed(on) }
        .onChange(of: settings.remindMolad) { _, on in changed(on) }
        .onChange(of: settings.israel) { _, _ in changed(false) }
    }

    private func header(_ s: String) -> some View {
        SmallCaps(s, lang: lang, size: 14, tracking: 1.4).foregroundStyle(Theme.accent)
    }

    private func toggle(_ label: String, _ value: Binding<Bool>) -> some View {
        Toggle(isOn: value) { Text(label).font(Theme.text(lang, 17)) }
    }

    /// Un rappel vient d'être activé : demander l'autorisation, puis tout reprogrammer.
    private func changed(_ enabled: Bool) {
        Task {
            if enabled {
                let ok = await Reminders.authorize()
                denied = !ok
                if !ok {
                    settings.remindRoshChodesh = false
                    settings.remindFeasts = false
                    settings.remindMolad = false
                    return
                }
            }
            await Reminders.reschedule(settings: settings, clock: model.clock, lang: lang)
        }
    }
}
