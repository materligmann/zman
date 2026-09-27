package technology.zman.core

// Portage de pkg/rega : l'unité fondamentale, le rega (1/76 de chelek), et
// la décomposition d'un nombre de rega'im depuis l'epoch.

object Rega {
    const val PER_CHELEK = 76L // Rambam, Hilchot Kiddush HaChodesh 10:1
    const val CHALAKIM_PER_HOUR = 1080L
    const val HOURS_PER_DAY = 24L

    const val PER_HOUR = PER_CHELEK * CHALAKIM_PER_HOUR // 82 080
    const val CHALAKIM_PER_DAY = CHALAKIM_PER_HOUR * HOURS_PER_DAY // 25 920
    const val PER_DAY = PER_CHELEK * CHALAKIM_PER_DAY // 1 969 920

    data class Parts(val day: Long, val hour: Int, val chelek: Int, val rega: Int)

    /** Décompose r ; composantes toujours dans [0, borne) même pour r négatif. */
    fun split(r: Long): Parts {
        val day = Math.floorDiv(r, PER_DAY)
        var rem = r - day * PER_DAY
        val hour = rem / PER_HOUR
        rem -= hour * PER_HOUR
        val chelek = rem / PER_CHELEK
        return Parts(day, hour.toInt(), chelek.toInt(), (rem - chelek * PER_CHELEK).toInt())
    }

    fun day(r: Long): Long = Math.floorDiv(r, PER_DAY)
    fun dayStart(day: Long): Long = day * PER_DAY
    fun fromChalakim(ch: Long): Long = ch * PER_CHELEK
}
