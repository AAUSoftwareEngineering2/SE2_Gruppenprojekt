package at.aau.kuhhandel.shared.websocket

import kotlinx.serialization.Serializable

@Serializable
enum class WebSocketType {
    // Client commands
    CREATE_GAME,
    CHANGE_SETTINGS,
    START_GAME,
    RESET_TO_LOBBY,
    JOIN_GAME,
    LEAVE_GAME,
    CHOOSE_AUCTION,
    RECONNECT,
    CHOOSE_TRADE,
    SUBMIT_TRADE_MONEY,
    RESPOND_TO_TRADE,
    PLACE_BID,
    RESOLVE_AUCTION,
    SUBMIT_AUCTION_PAYMENT,
    ADVANCE_TIMEOUT,
    FINISH_TRADE_REVEAL,
    SPY,
    CATCH_SPY,

    // Server events
    GAME_CREATED,
    GAME_STATE_UPDATED,
    GAME_JOINED,
    GAME_LEFT,
    SNAPSHOT,
    ERROR,
}
