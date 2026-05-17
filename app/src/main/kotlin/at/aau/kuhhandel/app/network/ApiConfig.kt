package at.aau.kuhhandel.app.network

/**
 * Central place for the backend location the Android app talks to.
 * Change here once and every caller follows along.
 */
object ApiConfig {
    const val HTTP_URL = "http://10.0.2.2:8080"
    const val WS_URL = "ws://10.0.2.2:8080"
}
