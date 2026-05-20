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
    INITIATE_TRADE,
    RESPOND_TO_TRADE,
    PLACE_BID,
    AUCTION_BUY_BACK,
    FINISH_TRADE_REVEAL,

    // Server events
    GAME_CREATED,
    GAME_STATE_UPDATED,
    GAME_JOINED,
    GAME_LEFT,
    SNAPSHOT,
    ERROR,
}
