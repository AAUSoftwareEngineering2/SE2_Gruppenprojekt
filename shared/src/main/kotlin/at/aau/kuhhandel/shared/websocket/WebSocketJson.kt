package at.aau.kuhhandel.shared.websocket

import kotlinx.serialization.json.Json

/**
 * Shared JSON configuration for WebSocket messages
 */
object WebSocketJson {
    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
}
