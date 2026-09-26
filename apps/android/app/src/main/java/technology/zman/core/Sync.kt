package technology.zman.core

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ZmanApi {
    const val BASE = "https://zman.technology"
    private const val NOW = "$BASE/api/now"

    /**
     * Interroge le serveur et mesure l'écart avec l'horloge locale, au milieu
     * de l'aller-retour (comme le site). Bloquant : à appeler hors du thread
     * principal.
     */
    fun calibrate(timeoutMillis: Int = 10_000): Calibration {
        val conn = URL(NOW).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMillis
            conn.readTimeout = timeoutMillis
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            val t0 = System.currentTimeMillis()
            val code = conn.responseCode
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val t1 = System.currentTimeMillis()
            check(code == 200) { "HTTP $code" }
            return calibration(body, t0, t1)
        } finally {
            conn.disconnect()
        }
    }

    fun calibration(json: String, sentAtMillis: Long, receivedAtMillis: Long): Calibration {
        val m = JSONObject(json)
        val clock = m.getJSONObject("clock")
        val local = Bridge.rega((sentAtMillis + receivedAtMillis) / 2)
        return Calibration(
            offsetRegaim = m.getLong("rega") - local,
            syncedAtMillis = receivedAtMillis,
            source = clock.getString("source"),
            dut1AgeDays = clock.optDouble("dut1_age_days", 0.0),
            predicted = clock.optBoolean("predicted", true),
        )
    }
}
