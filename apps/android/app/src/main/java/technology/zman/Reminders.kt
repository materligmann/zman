package technology.zman

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import technology.zman.core.Feast
import technology.zman.core.HebrewDate
import technology.zman.core.Lang
import technology.zman.core.Luach
import technology.zman.core.Moadim
import technology.zman.core.MoladAnnouncement
import technology.zman.core.Names
import technology.zman.core.Rega
import technology.zman.core.name
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Réglages de l'app, gardés dans les SharedPreferences de l'appareil (observables par Compose). */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("zman", Context.MODE_PRIVATE)

    /** En Israël : un seul jour de yom tov. Par défaut, selon la région de l'appareil. */
    var israel by pref("israel", Resources.getSystem().configuration.locales[0].country == "IL")
    var remindRoshChodesh by pref("remind.roshChodesh", false)
    var remindFeasts by pref("remind.feasts", false)
    var remindMolad by pref("remind.molad", false)

    val anyReminder: Boolean get() = remindRoshChodesh || remindFeasts || remindMolad

    private fun pref(key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        private var state by mutableStateOf(prefs.getBoolean(key, default))

        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = state

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            state = value
            prefs.edit().putBoolean(key, value).apply()
        }
    }
}

/**
 * Rappels : notifications locales, programmées sur l'appareil. Aucun serveur.
 *
 * Une fête est annoncée à la 22ᵉ heure de la veille, deux heures avant que
 * son jour commence (18:00 temps moyen de Jérusalem) ; un molad, à l'instant
 * du molad. Comme sur iOS, on programme les 60 prochains (une alarme inexacte
 * chacun) et on recommence à chaque ouverture de l'app et après un redémarrage.
 */
object Reminders {
    const val LIMIT = 60
    private const val CHANNEL = "reminders"
    private const val TITLE = "title"
    private const val BODY = "body"
    private const val ID = "id"

    data class Item(val id: String, val rega: Long, val title: String, val body: String)

    /** Les rappels à venir, à partir de l'instant `now` (en rega'im). */
    fun upcoming(
        now: Long,
        roshChodesh: Boolean,
        feasts: Boolean,
        molad: Boolean,
        israel: Boolean,
        lang: Lang,
    ): List<Item> {
        val tr = Tr(lang)
        val today = Luach.dateOf(Rega.day(now)) ?: return emptyList()
        val items = mutableListOf<Item>()
        for (year in listOf(today.year, today.year + 1)) {
            for (o in Moadim.year(year, israel)) {
                val wanted = when (o.feast.kind) {
                    Feast.Kind.ROSH_CHODESH -> roshChodesh
                    Feast.Kind.CHOL_HAMOED -> feasts && o.feast == Feast.HOSHANA_RABBA
                    else -> feasts
                }
                if (!wanted) continue
                val at = Rega.dayStart(o.start) - 2 * Rega.PER_HOUR
                val d = Luach.dateOf(o.start)!!
                val wd = Luach.weekday(o.start)
                val `when` = "${Names.weekday(wd, lang)} ${Names.dateTranslit(d, lang)}"
                val body = if (o.length > 1) {
                    tr(
                        "Commence ce soir : $`when`, pour ${tr.dayCount(o.length)}.",
                        "Begins tonight: $`when`, for ${tr.dayCount(o.length)}.",
                        "מתחיל הערב: ${Names.weekdayHe(wd)}, ${Names.dateHe(d)}, למשך ${tr.dayCount(o.length)}.",
                    )
                } else {
                    tr(
                        "Commence ce soir : $`when`.", "Begins tonight: $`when`.",
                        "מתחיל הערב: ${Names.weekdayHe(wd)}, ${Names.dateHe(d)}.",
                    )
                }
                items += Item("feast.${o.feast.name}.${o.start}", at, o.name(lang, year), body)
            }
            if (molad) {
                for (m in Luach.monthsInOrder(year)) {
                    val r = Luach.molad(year, m) ?: continue
                    val month = Names.month(HebrewDate(year, m, 1), lang)
                    val a = tr.announcement(MoladAnnouncement(r))
                    items += Item(
                        "molad.$year.$m", r,
                        tr("Molad de $month", "Molad of $month", "מולד $month"),
                        tr("La lune nouvelle, maintenant : $a.", "The new moon, now: $a.", "המולד עכשיו: $a."),
                    )
                }
            }
        }
        return items.filter { it.rega > now }.sortedBy { it.rega }.take(LIMIT)
    }

    /** Les notifications sont-elles permises (autorisation d'Android 13+, et réglage de l'app) ? */
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Remplace toutes les alarmes en attente par celles des réglages actuels. */
    fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        for (i in 0 until LIMIT) {
            PendingIntent.getBroadcast(
                context, i, Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let { am.cancel(it); it.cancel() }
        }
        val s = Settings(context)
        if (!s.anyReminder || !allowed(context)) return
        channel(context)
        val clock = CalibrationStore(context).clock()
        val items = upcoming(clock.rega(), s.remindRoshChodesh, s.remindFeasts, s.remindMolad, s.israel, context.lang())
        val now = System.currentTimeMillis()
        items.forEachIndexed { i, item ->
            // Le rega converti en instant de l'appareil, par la dernière calibration.
            val at = clock.unixMillisAt(item.rega)
            if (at - now <= 1000) return@forEachIndexed
            val intent = Intent(context, ReminderReceiver::class.java)
                .putExtra(ID, item.id).putExtra(TITLE, item.title).putExtra(BODY, item.body)
            val pi = PendingIntent.getBroadcast(
                context, i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun channel(context: Context) {
        val tr = Tr(context.lang())
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("Rappels", "Reminders", "תזכורות"), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    internal fun post(context: Context, intent: Intent) {
        if (!allowed(context)) return
        channel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = intent.getStringExtra(BODY).orEmpty()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_tab_moon)
            .setContentTitle(intent.getStringExtra(TITLE).orEmpty())
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(LightPalette.accent.toArgb())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(intent.getStringExtra(ID).hashCode(), n)
        } catch (_: SecurityException) {
            // Autorisation retirée entre-temps.
        }
    }
}

/** Une alarme de rappel arrive : afficher la notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.post(context, intent)
    }
}

/** Les alarmes ne survivent pas au redémarrage (ni à une mise à jour) : les reprogrammer. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pending = goAsync()
            Thread {
                try {
                    Reminders.reschedule(context)
                } finally {
                    pending.finish()
                }
            }.start()
        }
    }
}
