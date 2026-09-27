package technology.zman.wear

import android.content.Context
import technology.zman.core.Calibration
import technology.zman.core.Lang
import kotlin.math.roundToInt

/** Langue de l'interface, telle que résolue par les ressources (values, values-en, values-iw). */
fun Context.lang(): Lang = Lang.of(getString(R.string.lang))

fun Context.hourLabel(hour: Int): String = getString(R.string.hour_n, hour)

/** État de la mesure, comme sur le téléphone. */
fun Context.quality(c: Calibration?, offline: Boolean): String = when {
    c == null -> getString(if (offline) R.string.never_synced else R.string.syncing)
    offline -> getString(R.string.offline)
    c.source != "iers" -> getString(R.string.fallback)
    else -> getString(
        R.string.data_age,
        c.dut1AgeDays.roundToInt(),
        getString(if (c.predicted) R.string.predicted else R.string.measured),
    )
}
