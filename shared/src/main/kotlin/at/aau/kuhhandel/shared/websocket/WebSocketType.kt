package at.aau.kuhhandel.shared.websocket

import kotlinx.serialization.Serializable

@Serializable
enum class WebSocketType {
    CREATE_GAME,
    START_GAME,
    REVEAL_CARD,
    RECONNECT,
    GAME_CREATED,
    GAME_STARTED,
    MATCH_STATE_UPDATED,
    SNAPSHOT,
    ERROR,
}
