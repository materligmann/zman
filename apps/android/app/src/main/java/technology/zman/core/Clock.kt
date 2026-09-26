package technology.zman.core

// Portage de pkg/clock : le seul pont entre l'horloge de l'appareil (UTC,
// seconde SI) et les rega'im. Sans DUT1, c'est l'horloge « fallback » du
// serveur ; la calibration contre /api/now y ajoute la correction UT1 (et
// corrige au passage une horloge d'appareil déréglée).

object Bridge {
    /** Epoch → epoch Unix : 2 092 591,25 jours, en millisecondes. */
    private const val EPOCH_TO_UNIX_MILLIS = 180_799_884_000_000L
    /** Longitude de Jérusalem, 35,2137° E → +8 451,288 s. */
    private const val JERUSALEM_OFFSET_MILLIS = 8_451_288L
    /** 1 969 920 rega'im / 86 400 000 ms = 228 / 10 000. */
    private const val NUM = 228L
    private const val DEN = 10_000L

    /** Rega'im depuis l'epoch pour un instant Unix en millisecondes (UT1 ≈ UTC). */
    fun rega(unixMillis: Long): Long {
        val total = unixMillis + JERUSALEM_OFFSET_MILLIS + EPOCH_TO_UNIX_MILLIS
        val q = Math.floorDiv(total, DEN)
        return q * NUM + (total - q * DEN) * NUM / DEN
    }

    /** Premier instant Unix (ms) où le compteur atteint r. */
    fun unixMillis(r: Long): Long {
        val a = Math.floorDiv(r, NUM)
        val b = r - a * NUM
        val total = a * DEN + (b * DEN + NUM - 1) / NUM
        return total - JERUSALEM_OFFSET_MILLIS - EPOCH_TO_UNIX_MILLIS
    }
}

/** Correction mesurée contre le serveur, et qualité annoncée par lui. */
data class Calibration(
    val offsetRegaim: Long, // rega serveur − rega local (DUT1 + erreur de l'horloge locale)
    val syncedAtMillis: Long,
    val source: String, // "iers" ou "fallback"
    val dut1AgeDays: Double,
    val predicted: Boolean,
)

/** Un instant décomposé. */
data class Moment(val rega: Long) {
    private val parts = Rega.split(rega)
    val day: Long get() = parts.day
    val hour: Int get() = parts.hour
    val chelek: Int get() = parts.chelek
    val regaInChelek: Int get() = parts.rega
    val date: HebrewDate? = Luach.dateOf(parts.day)
    val weekday: Int get() = Luach.weekday(parts.day)

    val hourStart: Long get() = Rega.dayStart(day) + hour * Rega.PER_HOUR
    val hourEnd: Long get() = hourStart + Rega.PER_HOUR
}

class ZmanClock(val calibration: Calibration?) {
    private val offset get() = calibration?.offsetRegaim ?: 0L

    fun rega(unixMillis: Long = System.currentTimeMillis()): Long = Bridge.rega(unixMillis) + offset

    fun moment(unixMillis: Long = System.currentTimeMillis()): Moment = Moment(rega(unixMillis))

    /** Instant de l'appareil (ms Unix) où le compteur atteint r. */
    fun unixMillisAt(r: Long): Long = Bridge.unixMillis(r - offset)
}
