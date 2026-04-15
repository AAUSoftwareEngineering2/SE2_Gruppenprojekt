package at.aau.kuhhandel.shared.websocket

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Common wrapper for all client commands and server events.
 */
@Serializable
data class WebSocketEnvelope(
    val type: WebSocketType,
    val requestId: String? = null,
    val matchId: String? = null,
    val playerId: String? = null,
    val payload: JsonElement? = null,
)
