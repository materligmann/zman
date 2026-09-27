package technology.zman.wear

import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.AngularLayoutConstraint
import androidx.wear.protolayout.DimensionBuilders.DegreesProp
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Arc
import androidx.wear.protolayout.LayoutElementBuilders.ArcLine
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import technology.zman.core.Lang
import technology.zman.core.Moment
import technology.zman.core.Names
import technology.zman.core.ZmanClock
import java.time.Instant

/**
 * La tuile : date hébraïque, jour de la semaine, heure du jour, et la part
 * écoulée de l'heure en arc, que le système fait avancer seul. Une entrée de
 * timeline par heure du jour, sur 24 heures.
 */
class ZmanTileService : TileService() {
    private companion object {
        const val RESOURCES = "1"
        const val FRESHNESS_MILLIS = 3 * 3600_000L
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val clock = ZmanClock(WearSync.fresh(this@ZmanTileService))
            val now = System.currentTimeMillis()
            val timeline = TimelineBuilders.Timeline.Builder()
            var m = clock.moment(now)
            repeat(24) { i ->
                val start = clock.unixMillisAt(m.hourStart)
                val end = clock.unixMillisAt(m.hourEnd)
                timeline.addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder()
                        .setValidity(
                            TimelineBuilders.TimeInterval.Builder()
                                .setStartMillis(if (i == 0) now else start)
                                .setEndMillis(end)
                                .build(),
                        )
                        .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(layout(m, start, end)).build())
                        .build(),
                )
                m = Moment(m.hourEnd)
            }
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES)
                .setTileTimeline(timeline.build())
                .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture { ResourceBuilders.Resources.Builder().setVersion(RESOURCES).build() }

    private fun layout(m: Moment, startMillis: Long, endMillis: Long): LayoutElementBuilders.LayoutElement {
        val lang = lang()
        val d = m.date
        val column = Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        if (d != null) {
            column.addContent(text(if (lang == Lang.HE) Names.weekdayHe(m.weekday) else Names.weekday(m.weekday, lang).uppercase(), 13f, Wear.ACCENT))
            column.addContent(space(2f))
            column.addContent(text(Names.dayMonthHe(d), 26f, Wear.INK, medium = true))
            column.addContent(text(Names.gematria(d.year), 16f, Wear.INK2))
            if (lang != Lang.HE) column.addContent(text(Names.dateTranslit(d, lang), 14f, Wear.INK2))
            column.addContent(space(6f))
        }
        column.addContent(text(hourLabel(m.hour), 15f, Wear.INK))
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("open")
                            .setOnClick(
                                androidx.wear.protolayout.ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        androidx.wear.protolayout.ActionBuilders.AndroidActivity.Builder()
                                            .setPackageName(packageName)
                                            .setClassName(MainActivity::class.java.name)
                                            .build(),
                                    )
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .addContent(arc(DegreesProp.Builder(360f).build(), Wear.RULE))
            .addContent(arc(progress(startMillis, endMillis), Wear.ACCENT))
            .addContent(column.build())
            .build()
    }

    /** Part écoulée de l'heure, en degrés, recalculée par le système chaque seconde. */
    private fun progress(startMillis: Long, endMillis: Long): DegreesProp {
        val hourSeconds = (endMillis - startMillis) / 1000f
        val elapsed = ((System.currentTimeMillis() - startMillis) / 1000f).coerceIn(0f, hourSeconds)
        val remaining = DynamicInstant.platformTimeWithSecondsPrecision()
            .durationUntil(DynamicInstant.withSecondsPrecision(Instant.ofEpochMilli(endMillis)))
            .toIntSeconds()
            .asFloat()
        val degrees = DynamicFloat.constant(hourSeconds).minus(remaining).div(hourSeconds).times(360f)
        return DegreesProp.Builder(elapsed / hourSeconds * 360f).setDynamicValue(degrees).build()
    }

    private fun arc(length: DegreesProp, color: Int): Arc =
        Arc.Builder()
            .setAnchorAngle(DegreesProp.Builder(0f).build())
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(
                ArcLine.Builder()
                    .setLength(length)
                    // L'arc part de midi et grandit dans le sens horaire (sinon il est centré dans l'espace réservé).
                    .setLayoutConstraintsForDynamicLength(
                        AngularLayoutConstraint.Builder(360f)
                            .setAngularAlignment(LayoutElementBuilders.ANGULAR_ALIGNMENT_START)
                            .build(),
                    )
                    .setThickness(dp(4f))
                    .setColor(argb(color))
                    .build(),
            )
            .build()

    private fun text(s: String, size: Float, color: Int, medium: Boolean = false): Text =
        Text.Builder()
            .setText(s)
            .setMaxLines(1)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (medium) LayoutElementBuilders.FONT_WEIGHT_MEDIUM else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build(),
            )
            .build()

    private fun space(h: Float) = Spacer.Builder().setHeight(dp(h)).build()
}
