package technology.zman

import android.content.Context
import technology.zman.core.Calibration
import technology.zman.core.Lang
import technology.zman.core.ZmanClock

/** Dernière calibration, partagée entre l'app et le widget. */
class CalibrationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("zman", Context.MODE_PRIVATE)

    fun load(): Calibration? {
        if (!prefs.contains("offset")) return null
        return Calibration(
            offsetRegaim = prefs.getLong("offset", 0),
            syncedAtMillis = prefs.getLong("syncedAt", 0),
            source = prefs.getString("source", "fallback") ?: "fallback",
            dut1AgeDays = prefs.getFloat("dut1AgeDays", 0f).toDouble(),
            predicted = prefs.getBoolean("predicted", true),
        )
    }

    fun save(c: Calibration) {
        prefs.edit()
            .putLong("offset", c.offsetRegaim)
            .putLong("syncedAt", c.syncedAtMillis)
            .putString("source", c.source)
            .putFloat("dut1AgeDays", c.dut1AgeDays.toFloat())
            .putBoolean("predicted", c.predicted)
            .apply()
    }

    fun clock(): ZmanClock = ZmanClock(load())
}

/** Langue de l'interface, telle que résolue par les ressources (values, values-en, values-iw). */
fun Context.lang(): Lang = Lang.of(getString(R.string.lang))
