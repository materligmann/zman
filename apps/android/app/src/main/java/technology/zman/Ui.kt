package technology.zman

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import technology.zman.core.Names
import technology.zman.core.ZmanClock

// Pièces communes aux onglets : barre de titre, sélecteur d'année, lignes de
// faits, horloge qui bat. Les équivalents des vues SwiftUI partagées.

/** Le compteur de rega'im, relu toutes les `periodMillis` (TimelineView). */
@Composable
fun rememberRega(clock: ZmanClock, periodMillis: Long): State<Long> =
    produceState(clock.rega(), clock.calibration, periodMillis) {
        while (true) {
            value = clock.rega()
            delay(periodMillis)
        }
    }

/** Titre en ligne, retour à gauche (à droite en hébreu), action éventuelle de l'autre côté. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, backLabel: String = "", action: Pair<String, () -> Unit>? = null) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 6.dp)) {
        if (onBack != null) {
            IconButton(R.drawable.ic_chevron_back, backLabel, Modifier.align(Alignment.CenterStart), onBack)
        }
        ZText(title, 17.sp, p.ink, weight = FontWeight.Medium, maxLines = 1, modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp))
        if (action != null) {
            ZText(
                action.first, 17.sp, p.accent,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(role = Role.Button, onClick = action.second)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun IconButton(@DrawableRes icon: Int, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(10.dp),
    ) {
        Image(
            painterResource(icon), null, Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(LocalPalette.current.accent),
        )
    }
}

/** Page défilante centrée, de largeur bornée, sur le papier. */
@Composable
fun ScrollPage(maxWidth: Dp = 560.dp, horizontal: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(horizontal = horizontal, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** Carte de papier plus sombre (compte à rebours, jour choisi). */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(LocalPalette.current.paper2, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun Rule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalPalette.current.rule))
}

@Composable
fun Dot(color: Color, size: Dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

/** En-tête de section en petites capitales, aligné au début. */
@Composable
fun SectionTitle(text: String, size: Int = 15) {
    SmallCaps(text, size.sp, LocalPalette.current.accent, tracking = 0.1.em, modifier = Modifier.fillMaxWidth(), align = TextAlign.Start)
}

/** Libellé et valeur, filet dessous. */
@Composable
fun Fact(label: String, value: String) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        ZText(label, 16.sp, p.ink, weight = FontWeight.Medium, align = TextAlign.Start, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(2.dp))
        ZText(value, 15.sp, p.ink2, align = TextAlign.Start, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Rule()
    }
}

/** Année précédente / suivante, en lettres et en chiffres. */
@Composable
fun YearPicker(year: Long, tr: Tr, onChange: (Long) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(R.drawable.ic_chevron_back, tr("Année précédente", "Previous year", "השנה הקודמת")) {
            if (year > 1) onChange(year - 1)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            ZText(Names.gematria(year), 24.sp, p.ink, weight = FontWeight.Medium, rtl = true)
            ZText(year.toString(), 15.sp, p.ink2, tnum = true)
        }
        IconButton(R.drawable.ic_chevron_forward, tr("Année suivante", "Next year", "השנה הבאה")) { onChange(year + 1) }
    }
}
