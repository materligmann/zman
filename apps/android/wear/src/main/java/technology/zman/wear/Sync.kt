package technology.zman.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import technology.zman.CalibrationStore
import technology.zman.core.Calibration
import technology.zman.core.ZmanApi
import kotlin.math.abs

/** Resynchronisation contre le serveur, partagée par l'app, la tuile et les complications. */
object WearSync {
    private const val RESYNC_AFTER_MILLIS = 3 * 3600_000L

    /** Recale si la dernière calibration a plus de 3 heures ; rend la calibration à utiliser. */
    suspend fun fresh(context: Context): Calibration? {
        val store = CalibrationStore(context)
        val last = store.load()
        if (last != null && System.currentTimeMillis() - last.syncedAtMillis < RESYNC_AFTER_MILLIS) return last
        val c = runCatching { withContext(Dispatchers.IO) { ZmanApi.calibrate(timeoutMillis = 5_000) } }.getOrNull()
            ?: return last
        store.save(c)
        return c
    }

    /** Après une calibration par l'app : prévenir la tuile et les complications si elle a bougé. */
    fun changed(context: Context, previous: Calibration?, c: Calibration) {
        if (previous != null && abs(previous.offsetRegaim - c.offsetRegaim) <= 2 && previous.source == c.source) return
        refresh(context)
    }

    fun refresh(context: Context) {
        TileService.getUpdater(context).requestUpdate(ZmanTileService::class.java)
        ComplicationDataSourceUpdateRequester
            .create(context, ComponentName(context, ZmanComplicationService::class.java))
            .requestUpdateAll()
    }
}
