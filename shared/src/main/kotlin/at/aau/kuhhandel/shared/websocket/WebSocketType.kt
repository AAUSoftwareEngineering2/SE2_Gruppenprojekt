package at.aau.kuhhandel.shared.websocket

import kotlinx.serialization.Serializable

@Serializable
enum class WebSocketType {
    // Client commands
    CREATE_GAME,
    START_GAME,
    REVEAL_CARD,
    RECONNECT,

    // Server Side TODO: Add types for the core game loop:
    // PLACE_BID, AUCTION_BUY_BACK, INITIATE_TRADE, RESPOND_TO_TRADE

    // Server events
    GAME_CREATED,
    GAME_STARTED,
    GAME_STATE_UPDATED,
    SNAPSHOT,
    ERROR,
}
