package technology.zman.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import technology.zman.CalibrationStore
import technology.zman.core.ZmanApi
import java.util.concurrent.TimeUnit

/**
 * Redessine le widget : toutes les 15 minutes (avec resynchronisation contre
 * le serveur quand la dernière date de plus de 3 heures), et au début de
 * chaque heure du jour.
 */
object WidgetUpdater {
    private const val PERIODIC = "zman-widget-periodic"
    private const val HOURLY = "zman-widget-hour"
    private const val RESYNC_AFTER_MILLIS = 3 * 3600_000L

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateWorker>(15, TimeUnit.MINUTES)
                .setInputData(workDataOf(UpdateWorker.SYNC to true))
                .build(),
        )
        scheduleNextHour(context)
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(PERIODIC)
        wm.cancelUniqueWork(HOURLY)
    }

    /** Redessin immédiat (par exemple après une calibration faite par l'app). */
    fun refresh(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<UpdateWorker>().build())
    }

    fun scheduleNextHour(context: Context) {
        val clock = CalibrationStore(context).clock()
        val next = clock.unixMillisAt(clock.moment().hourEnd)
        // Un peu après la frontière, pour tomber du bon côté.
        val delay = (next - System.currentTimeMillis()).coerceAtLeast(0) + 500
        WorkManager.getInstance(context).enqueueUniqueWork(
            HOURLY,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<UpdateWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(UpdateWorker.NEXT_HOUR to true))
                .build(),
        )
    }

    internal suspend fun maybeSync(context: Context) {
        val store = CalibrationStore(context)
        val last = store.load()?.syncedAtMillis ?: 0
        if (System.currentTimeMillis() - last < RESYNC_AFTER_MILLIS) return
        runCatching { ZmanApi.calibrate(timeoutMillis = 5_000) }.onSuccess(store::save)
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val SYNC = "sync"
        const val NEXT_HOUR = "nextHour"
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (GlanceAppWidgetManager(ctx).getGlanceIds(ZmanWidget::class.java).isEmpty()) return Result.success()
        if (inputData.getBoolean(SYNC, false)) WidgetUpdater.maybeSync(ctx)
        ZmanWidget().updateAll(ctx)
        // Chaîne horaire : se reprogrammer ; le passage périodique la relance si elle a sauté.
        if (inputData.getBoolean(NEXT_HOUR, false) || inputData.getBoolean(SYNC, false)) WidgetUpdater.scheduleNextHour(ctx)
        return Result.success()
    }
}
