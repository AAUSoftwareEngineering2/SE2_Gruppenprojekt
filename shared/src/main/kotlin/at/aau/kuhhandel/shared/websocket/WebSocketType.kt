package at.aau.kuhhandel.shared.websocket

import kotlinx.serialization.Serializable

@Serializable
enum class WebSocketType {
    // Client commands
    CREATE_GAME,
    START_GAME,
    JOIN_GAME,
    LEAVE_GAME,
    CHOOSE_AUCTION,
    RECONNECT,
    CHOOSE_TRADE,
    RESPOND_TO_TRADE,
    PLACE_BID,
    RESOLVE_AUCTION,
    FINISH_TRADE_REVEAL,

    // Server events
    GAME_CREATED,
    GAME_STATE_UPDATED,
    GAME_JOINED,
    GAME_LEFT,
    SNAPSHOT,
    ERROR,
}
