package at.aau.kuhhandel.shared.websocket

import kotlinx.serialization.Serializable

@Serializable
enum class WebSocketType {
    // Client commands
    CREATE_GAME,
    START_GAME,
    REVEAL_CARD,
    RECONNECT,

    // Server events
    GAME_CREATED,
    GAME_STARTED,
    GAME_STATE_UPDATED,
    SNAPSHOT,
    ERROR,
}
