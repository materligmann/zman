package technology.zman

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import technology.zman.core.Calibration
import technology.zman.core.ZmanApi
import technology.zman.widget.WidgetUpdater
import kotlin.math.abs

/** État de synchronisation observé par l'écran. */
data class SyncState(val calibration: Calibration?, val offline: Boolean)

class MainActivity : ComponentActivity() {
    companion object {
        /**
         * Onglet de départ, pour les captures des stores :
         * `adb shell am start -n studiocentmoinshuit.zman/technology.zman.MainActivity --ei tab 2`.
         */
        const val EXTRA_TAB = "tab"
    }

    private lateinit var store: CalibrationStore
    private lateinit var settings: Settings
    private val state = MutableStateFlow(SyncState(null, false))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = CalibrationStore(this)
        settings = Settings(this)
        state.value = SyncState(store.load(), false)
        // Le widget suit la langue de l'app : un changement de langue recrée l'activité.
        WidgetUpdater.refresh(this)

        setContent {
            CompositionLocalProvider(LocalPalette provides palette()) {
                Root(state, settings, lang(), initialTab = intent.getIntExtra(EXTRA_TAB, 0))
            }
        }

        // Au premier plan : synchroniser tout de suite, puis chaque minute.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Les rappels ne couvrent que les 60 prochains : les renouveler à chaque retour.
                if (settings.anyReminder) withContext(Dispatchers.Default) { Reminders.reschedule(this@MainActivity) }
                while (true) {
                    sync()
                    delay(60_000)
                }
            }
        }
    }

    private suspend fun sync() {
        val c = try {
            withContext(Dispatchers.IO) { ZmanApi.calibrate() }
        } catch (e: Exception) {
            state.value = state.value.copy(offline = true)
            return
        }
        val previous = state.value.calibration
        store.save(c)
        state.value = SyncState(c, false)
        // Le widget calcule avec la même correction : le prévenir si elle a bougé.
        if (previous == null || abs(previous.offsetRegaim - c.offsetRegaim) > 2 || previous.source != c.source) {
            WidgetUpdater.refresh(this)
        }
    }
}
