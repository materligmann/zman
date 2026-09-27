package technology.zman.wear

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import technology.zman.core.Lang
import technology.zman.core.Moment
import technology.zman.core.Names
import technology.zman.core.Rega
import technology.zman.core.ZmanClock

/**
 * Complications du cadran : l'heure du jour (texte court), la date hébraïque
 * et l'heure (texte long), la part écoulée de l'heure (jauge).
 */
class ZmanComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        data(type, ZmanClock(null).moment())

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        data(request.complicationType, ZmanClock(WearSync.fresh(this)).moment())

    private fun data(type: ComplicationType, m: Moment): ComplicationData? {
        val lang = lang()
        val d = m.date
        val hour = plain(m.hour.toString())
        val hourTitle = plain(getString(R.string.hour))
        val label = hourLabel(m.hour)
        val date = d?.let { if (lang == Lang.HE) Names.dayMonthHe(it) else "${it.day} ${Names.month(it, lang)}" } ?: ""
        val description = plain("$date · $label")
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(hour, description).setTitle(hourTitle).setTapAction(tap).build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(plain(label), description)
                    .setTitle(plain(d?.let(Names::dayMonthHe) ?: ""))
                    .setTapAction(tap)
                    .build()
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = (m.chelek * Rega.PER_CHELEK + m.regaInChelek).toFloat(),
                    min = 0f,
                    max = Rega.PER_HOUR.toFloat(),
                    contentDescription = description,
                ).setText(hour).setTitle(hourTitle).setTapAction(tap).build()
            else -> null
        }
    }

    private fun plain(s: String) = PlainComplicationText.Builder(s).build()
}
