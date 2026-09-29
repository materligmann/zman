package technology.zman

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import technology.zman.core.Lang
import technology.zman.core.ZmanClock

/** Les cinq onglets : l'horloge, le luach, les fêtes, le molad, et le reste. */
private class Tab(@DrawableRes val icon: Int, val label: (Tr) -> String)

private val TABS = listOf(
    Tab(R.drawable.ic_tab_clock) { it("Horloge", "Clock", "שעון") },
    Tab(R.drawable.ic_tab_calendar) { it("Luach", "Luach", "לוח") },
    Tab(R.drawable.ic_tab_sparkles) { it("Fêtes", "Festivals", "מועדים") },
    Tab(R.drawable.ic_tab_moon) { it("Molad", "Molad", "מולד") },
    Tab(R.drawable.ic_tab_book) { it("Plus", "More", "עוד") },
)

/**
 * Racine de l'app : barre d'onglets en bas et, par onglet, une pile de pages
 * du site ouvertes par-dessus (comme les NavigationStack d'iOS).
 */
@Composable
fun Root(stateFlow: StateFlow<SyncState>, settings: Settings, lang: Lang, initialTab: Int) {
    val p = LocalPalette.current
    val tr = Tr(lang)
    val state by stateFlow.collectAsState()
    val clock = ZmanClock(state.calibration)
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, TABS.lastIndex)) }
    // Pages du site ouvertes dans chaque onglet (non gardées à la recréation de l'activité).
    val stacks = remember { List(TABS.size) { mutableStateListOf<Page>() } }
    val stack = stacks[tab]
    val holder = rememberSaveableStateHolder()
    val open: (Page) -> Unit = { stack.add(it) }
    val direction = if (lang.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Column(Modifier.fillMaxSize().background(p.paper)) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            ) {
                // Chaque onglet (et chaque profondeur de pile) garde son état quand on en change.
                holder.SaveableStateProvider("$tab/${stack.size}") {
                    if (stack.isNotEmpty()) {
                        val page = stack.last()
                        Column {
                            TopBar(
                                page.title(lang),
                                onBack = { stack.removeAt(stack.lastIndex) },
                                backLabel = tr("Retour", "Back", "חזרה"),
                            )
                            ReaderScreen(page, lang, open)
                        }
                    } else {
                        when (tab) {
                            0 -> ClockScreen(stateFlow, settings, lang, open)
                            1 -> LuachScreen(clock, settings, lang)
                            2 -> MoadimScreen(clock, settings, lang)
                            3 -> MoladScreen(clock, lang)
                            else -> MoreScreen(settings, lang, open)
                        }
                    }
                }
            }
            TabBar(tab, tr) { i ->
                // Toucher l'onglet déjà choisi ramène à sa racine.
                if (i == tab) stacks[i].clear()
                tab = i
            }
        }
    }
}

@Composable
private fun TabBar(selected: Int, tr: Tr, select: (Int) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(p.paper2).navigationBarsPadding()) {
        Rule()
        Row(Modifier.fillMaxWidth().height(58.dp).selectableGroup()) {
            TABS.forEachIndexed { i, t ->
                val color = if (i == selected) p.accent else p.ink2
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .selectable(i == selected, role = Role.Tab) { select(i) }
                        .padding(top = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(t.icon), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(color))
                    Spacer(Modifier.height(3.dp))
                    ZText(t.label(tr), 12.sp, color, maxLines = 1)
                }
            }
        }
    }
}
