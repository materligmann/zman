package technology.zman.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import technology.zman.CalibrationStore
import technology.zman.core.Calibration
import technology.zman.core.ZmanApi

/** État de synchronisation observé par l'écran. */
data class SyncState(val calibration: Calibration?, val offline: Boolean)

class MainActivity : ComponentActivity() {
    private lateinit var store: CalibrationStore
    private val state = MutableStateFlow(SyncState(null, false))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CalibrationStore(this)
        state.value = SyncState(store.load(), false)

        setContent { WatchClockScreen(state, lang()) }

        // Au premier plan : synchroniser tout de suite, puis chaque minute.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
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
        WearSync.changed(this, previous, c)
    }
}
