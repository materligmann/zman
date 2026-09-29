package technology.zman

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import android.provider.Settings.EXTRA_APP_PACKAGE
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import technology.zman.core.Lang
import technology.zman.core.ZmanApi
import technology.zman.widget.ZmanWidgetReceiver

/** Rappels, réglage Israël / diaspora, les textes du site, le widget et la montre. */
@Composable
fun MoreScreen(settings: Settings, lang: Lang, open: (Page) -> Unit) {
    val tr = Tr(lang)
    val p = LocalPalette.current
    val context = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Hors du thread principal : 60 alarmes, autant d'appels au système.
    fun reschedule() {
        scope.launch(Dispatchers.Default) { Reminders.reschedule(context.applicationContext) }
    }

    fun refuse() {
        denied = true
        settings.remindRoshChodesh = false
        settings.remindFeasts = false
        settings.remindMolad = false
        reschedule()
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            denied = false
            reschedule()
        } else {
            refuse()
        }
    }

    /** Un réglage vient de changer : demander l'autorisation s'il faut, puis tout reprogrammer. */
    fun changed(enabled: Boolean) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                refuse()
                return
            }
            denied = false
        }
        reschedule()
    }

    Column {
        TopBar(tr("Plus", "More", "עוד"))
        ScrollPage {
            Section(tr("Rappels", "Reminders", "תזכורות")) {
                Toggle(tr("Roch Hodech", "Rosh Chodesh", "ראש חודש"), null, settings.remindRoshChodesh) {
                    settings.remindRoshChodesh = it; changed(it)
                }
                Rule()
                Toggle(tr("Fêtes et jeûnes", "Festivals and fasts", "מועדים ותעניות"), null, settings.remindFeasts) {
                    settings.remindFeasts = it; changed(it)
                }
                Rule()
                Toggle(tr("Molad", "Molad", "מולד"), null, settings.remindMolad) {
                    settings.remindMolad = it; changed(it)
                }
            }
            Footer(
                if (denied) {
                    tr(
                        "Les notifications sont refusées pour Zman : autorisez-les dans Paramètres › Applications › Zman › Notifications.",
                        "Notifications are turned off for Zman: allow them in Settings › Apps › Zman › Notifications.",
                        "ההתראות של זמן כבויות: אפשרו אותן בהגדרות › אפליקציות › Zman › התראות.",
                    )
                } else {
                    tr(
                        "Une notification à la 22ᵉ heure de la veille, deux heures avant que le jour commence ; pour le molad, à l’instant du molad. Tout est programmé sur l’appareil.",
                        "A notification at the 22nd hour of the day before, two hours before the day begins; for the molad, at the moment of the molad. Everything is scheduled on the device.",
                        "התראה בשעה ה־22 של היום הקודם, שעתיים לפני שהיום מתחיל; ובמולד, ברגע המולד. הכול מתוזמן במכשיר עצמו.",
                    )
                },
                onClick = if (denied) {
                    {
                        context.startActivity(
                            Intent(ACTION_APP_NOTIFICATION_SETTINGS).putExtra(EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                } else null,
            )

            Section(tr("Calendrier", "Calendar", "לוח")) {
                Toggle(
                    tr("En Israël", "In Israel", "בארץ ישראל"),
                    tr("Un seul jour de fête (sans יום טוב שני)", "One festival day (no יום טוב שני)", "יום טוב אחד, ללא יום טוב שני של גלויות"),
                    settings.israel,
                ) {
                    settings.israel = it; changed(false)
                }
            }

            Section(tr("Lire", "Read", "לקריאה")) {
                Page.entries.forEachIndexed { i, page ->
                    if (i > 0) Rule()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { open(page) }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            ZText(page.title(lang), 17.sp, p.ink, align = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                            ZText(page.summary(lang), 13.sp, p.ink2, align = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                        }
                        androidx.compose.foundation.Image(
                            androidx.compose.ui.res.painterResource(R.drawable.ic_chevron_forward), null,
                            Modifier.size(16.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(p.ink2),
                        )
                    }
                }
            }

            Section(tr("Widget et montre", "Widget and watch", "ווידג׳ט ושעון")) {
                WidgetPromo()
                Rule()
                Plain(
                    tr(
                        "Sur une montre Wear OS, installez Zman depuis le Play Store de la montre ; ajoutez sa tuile, et ses complications depuis l’édition du cadran.",
                        "On a Wear OS watch, install Zman from the watch’s Play Store; add its tile, and its complications by editing the watch face.",
                        "בשעון Wear OS, התקינו את זמן מחנות Play שבשעון; הוסיפו את האריח שלו, ואת הסיבוכים בעריכת פני השעון.",
                    ),
                )
            }

            Section(null) {
                val uri = LocalUriHandler.current
                ZText(
                    "zman.technology", 17.sp, p.accent, align = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { uri.openUri("${ZmanApi.BASE}/${lang.name.lowercase()}/") }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                )
                Rule()
                ZText(
                    tr(
                        "Aucun compte, aucune publicité, aucun suivi. Code sous licence MIT, données de l’API sous CC BY 4.0.",
                        "No account, no ads, no tracking. Code under the MIT licence, API data under CC BY 4.0.",
                        "ללא חשבון, ללא פרסומות, ללא מעקב. הקוד ברישיון MIT, נתוני ה־API ברישיון CC BY 4.0.",
                    ),
                    13.sp, p.ink2, align = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                )
            }
        }
    }
}

/** Section de liste : en-tête en petites capitales, lignes sur papier plus sombre. */
@Composable
private fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(18.dp))
    if (title != null) {
        Box(Modifier.padding(horizontal = 16.dp)) { SectionTitle(title, 14) }
        Spacer(Modifier.height(6.dp))
    }
    Column(
        Modifier.fillMaxWidth().background(LocalPalette.current.paper2, RoundedCornerShape(12.dp)),
        content = content,
    )
}

@Composable
private fun Footer(text: String, onClick: (() -> Unit)?) {
    ZText(
        text, 13.sp, LocalPalette.current.ink2, align = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Plain(text: String) {
    ZText(
        text, 15.sp, LocalPalette.current.ink, align = TextAlign.Start,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
    )
}

/** Ligne à interrupteur, sous-titre éventuel. */
@Composable
private fun Toggle(label: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            ZText(label, 17.sp, p.ink, align = TextAlign.Start, modifier = Modifier.fillMaxWidth())
            if (detail != null) ZText(detail, 13.sp, p.ink2, align = TextAlign.Start, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.size(12.dp))
        val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "toggle")
        Box(
            Modifier
                .size(width = 46.dp, height = 26.dp)
                .background(if (checked) p.accent else p.rule, RoundedCornerShape(50))
                .padding(3.dp),
        ) {
            Box(Modifier.offset(x = x).size(20.dp).background(p.paper, CircleShape))
        }
    }
}

/**
 * Le widget n'est visible nulle part ailleurs : l'app le signale et, si le
 * lanceur le permet, propose de l'épingler directement.
 */
@Composable
private fun WidgetPromo() {
    val p = LocalPalette.current
    val context = LocalContext.current
    val canPin = remember { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
        ZText(stringResource(R.string.widget_hint), 15.sp, p.ink, align = TextAlign.Start, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        if (canPin) {
            ZText(
                stringResource(R.string.widget_add), 16.sp, p.accent,
                modifier = Modifier
                    .border(1.dp, p.accent, RoundedCornerShape(50))
                    .clickable(role = Role.Button) {
                        AppWidgetManager.getInstance(context).requestPinAppWidget(
                            ComponentName(context, ZmanWidgetReceiver::class.java), null, null,
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        } else {
            ZText(stringResource(R.string.widget_manual), 14.sp, p.ink2, align = TextAlign.Start, modifier = Modifier.fillMaxWidth())
        }
    }
}
