package at.aau.kuhhandel.shared.websocket

import kotlinx.serialization.Serializable

@Serializable
enum class WebSocketType {
    // Client commands
    CREATE_GAME,
    START_GAME,
    REVEAL_CARD,
    RECONNECT,
    INITIATE_TRADE,
    OFFER_TRADE,
    RESPOND_TO_TRADE,

    // Server Side TODO: Add types for the auction loop:
    // PLACE_BID, AUCTION_BUY_BACK

    // Server events
    GAME_CREATED,
    GAME_STARTED,
    GAME_STATE_UPDATED,
    SNAPSHOT,
    ERROR,
}
