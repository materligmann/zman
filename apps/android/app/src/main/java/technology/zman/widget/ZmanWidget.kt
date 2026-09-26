package technology.zman.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import technology.zman.CalibrationStore
import technology.zman.DarkPalette
import technology.zman.LightPalette
import technology.zman.MainActivity
import technology.zman.R
import technology.zman.core.Lang
import technology.zman.core.Moment
import technology.zman.core.Names
import technology.zman.core.Rega
import technology.zman.lang

// Le widget ne compte pas les rega'im en direct : il affiche la date et
// l'heure du jour, et la part écoulée de l'heure. WidgetUpdater le redessine
// à chaque début d'heure et toutes les 15 minutes.

private fun color(pick: (technology.zman.Palette) -> Color): ColorProvider =
    ColorProvider(day = pick(LightPalette), night = pick(DarkPalette))

private val Paper = color { it.paper }
private val Ink = color { it.ink }
private val Ink2 = color { it.ink2 }
private val Rule = color { it.rule }
private val Accent = color { it.accent }

class ZmanWidget : GlanceAppWidget() {
    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val WIDE = DpSize(250.dp, 110.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val clock = CalibrationStore(context).clock()
        val lang = context.lang()
        val hourLabel = context.getString(R.string.hour_n)
        provideContent {
            val m = clock.moment()
            Content(m, lang, hourLabel)
        }
    }

    @Composable
    private fun Content(m: Moment, lang: Lang, hourLabel: String) {
        val wide = LocalSize.current.width >= WIDE.width
        Box(
            GlanceModifier
                .fillMaxSize()
                .background(Paper)
                .cornerRadius(22.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            if (wide) Wide(m, lang, hourLabel) else Small(m, lang, hourLabel)
        }
    }

    @Composable
    private fun Small(m: Moment, lang: Lang, hourLabel: String) {
        val d = m.date ?: return
        // Jour de la semaine et translittération dans la langue de l'appareil ;
        // la date hébraïque reste l'élément central.
        val he = lang == Lang.HE
        val align = if (he) TextAlign.End else TextAlign.Start
        Column(GlanceModifier.fillMaxSize(), horizontalAlignment = if (he) Alignment.End else Alignment.Start) {
            if (he) Text(Names.weekdayHe(m.weekday), style = serif(13, Accent, align))
            else Text(Names.weekday(m.weekday, lang).uppercase(), style = serif(12, Accent, align))
            Spacer(GlanceModifier.defaultWeight())
            Text(Names.dayMonthHe(d), style = serif(26, Ink, align, FontWeight.Medium), maxLines = 1)
            if (he) Text(Names.gematria(d.year), style = serif(15, Ink2, align))
            else Text(Names.dateTranslit(d, lang), style = serif(14, Ink2, align), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            Text(hourLabel.format(m.hour).let { if (he) it else it.uppercase() }, style = label(lang, align))
            Spacer(GlanceModifier.height(4.dp))
            HourProgress(m)
        }
    }

    @Composable
    private fun Wide(m: Moment, lang: Lang, hourLabel: String) {
        val d = m.date ?: return
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Names.dayMonthHe(d), style = serif(30, Ink, TextAlign.Center, FontWeight.Medium), maxLines = 1)
                Text(Names.gematria(d.year), style = serif(18, Ink2, TextAlign.Center))
                Spacer(GlanceModifier.height(4.dp))
                Text(Names.weekdayHe(m.weekday), style = serif(14, Accent, TextAlign.Center))
            }
            Spacer(GlanceModifier.width(12.dp))
            Box(GlanceModifier.width(1.dp).fillMaxHeight().background(Rule)) {}
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                Text(Names.dateTranslit(d, lang), style = serif(16, Ink), maxLines = 1)
                if (lang != Lang.HE) {
                    Text(Names.weekday(m.weekday, lang).uppercase(), style = serif(12, Accent))
                }
                Spacer(GlanceModifier.defaultWeight())
                Text(hourLabel.format(m.hour).let { if (lang == Lang.HE) it else it.uppercase() }, style = label(lang))
                Spacer(GlanceModifier.height(4.dp))
                HourProgress(m)
            }
        }
    }

    @Composable
    private fun HourProgress(m: Moment) {
        val elapsed = (m.rega - m.hourStart).toFloat() / Rega.PER_HOUR
        LinearProgressIndicator(
            progress = elapsed,
            modifier = GlanceModifier.fillMaxWidth().height(3.dp),
            color = Accent,
            backgroundColor = Rule,
        )
    }

    private fun serif(size: Int, color: ColorProvider, align: TextAlign = TextAlign.Start, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = FontFamily.Serif, fontSize = size.sp, color = color, textAlign = align, fontWeight = weight)

    private fun label(lang: Lang, align: TextAlign = TextAlign.Start) =
        serif(if (lang == Lang.HE) 13 else 11, Ink2, align)
}

class ZmanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ZmanWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.cancel(context)
    }
}
