package at.aau.kuhhandel.shared.enums

import kotlinx.serialization.Serializable

@Serializable
enum class GameErrorReason {
    // Internal (only used in an ErrorPayload and never in a thrown GameException)
    INTERNAL_SERVER_ERROR,

    // Communication
    INVALID_MESSAGE_FORMAT,
    UNSUPPORTED_MESSAGE_TYPE,
    MISSING_PAYLOAD,
    INVALID_PAYLOAD,

    // Session connection
    CONNECTION_ALREADY_BOUND,
    CONNECTION_NOT_BOUND,
    GAME_NOT_FOUND,
    INVALID_RECONNECTION_TOKEN,

    // Game flow
    INVALID_PHASE,
    UNKNOWN_ACTOR,
    UNKNOWN_PLAYER,
    ALREADY_CONNECTED,
    ALREADY_DISCONNECTED,
    NOT_YOUR_TURN,

    ALREADY_IN_ROOM,
    NOT_ENOUGH_PLAYERS,
    ROOM_FULL,
    NOT_HOST,
    INVALID_PLAYER_NAME,

    DECK_EMPTY,
    OWN_AUCTION,
    EXCLUDED_FROM_AUCTION,
    BID_TOO_LOW,
    BID_TOO_HIGH,
    NOT_ENOUGH_MONEY,
    NOT_AUCTIONEER,

    UNKNOWN_TARGET,
    TARGETING_SELF,

    INITIATOR_MISSING_ANIMAL,
    TARGET_MISSING_ANIMAL,
    NOT_OWNED_MONEY_CARDS,
    NOT_TRADE_INITIATOR,
    NOT_TRADE_TARGET,

    ACTIVE_PLAYER_CANNOT_SPY,
    ALREADY_SPIED_THIS_TURN,
    CANNOT_SPY_WITHOUT_MONEY,
    CANNOT_CATCH_WHILE_SPYING,
    NOT_SPIED_UPON,
    ;

    /**
     * Returns a human-readable message for this error reason.
     */
    fun toUserMessage(): String =
        when (this) {
            INTERNAL_SERVER_ERROR -> "An unexpected server error occurred."
            INVALID_MESSAGE_FORMAT, UNSUPPORTED_MESSAGE_TYPE, MISSING_PAYLOAD, INVALID_PAYLOAD ->
                "Invalid communication with server."

            CONNECTION_ALREADY_BOUND -> "You are already connected to a game."
            CONNECTION_NOT_BOUND -> "Connection lost. Please try again."
            GAME_NOT_FOUND -> "Game not found. Please check the code."
            INVALID_RECONNECTION_TOKEN -> "Session expired. Please rejoin."
            INVALID_PHASE -> "This action is not allowed right now."
            UNKNOWN_ACTOR -> "Invalid player action."
            UNKNOWN_PLAYER -> "You are not a player in this game."
            ALREADY_CONNECTED -> "You are already connected to the game."
            ALREADY_DISCONNECTED -> "You have already been disconnected from the game."
            NOT_YOUR_TURN -> "It is not your turn."
            ALREADY_IN_ROOM -> "You are already in this room."
            NOT_ENOUGH_PLAYERS -> "Not enough players to start."
            ROOM_FULL -> "The room is full."
            NOT_HOST -> "Only the host can perform this action."
            INVALID_PLAYER_NAME -> "Invalid player name."
            DECK_EMPTY -> "The deck is empty."
            OWN_AUCTION -> "You cannot bid on your own auction."
            EXCLUDED_FROM_AUCTION -> "You are excluded from this auction."
            BID_TOO_LOW -> "Your bid is too low."
            BID_TOO_HIGH -> "Your bid is higher than your money."
            NOT_ENOUGH_MONEY -> "You do not have enough money."
            NOT_AUCTIONEER -> "You are not the auctioneer."
            UNKNOWN_TARGET -> "Target player not found."
            TARGETING_SELF -> "You cannot target yourself."
            INITIATOR_MISSING_ANIMAL -> "You don't have the required animal."
            TARGET_MISSING_ANIMAL -> "Target player doesn't have the required animal."
            NOT_OWNED_MONEY_CARDS -> "You don't own these money cards."
            NOT_TRADE_INITIATOR -> "You are not the trade initiator."
            NOT_TRADE_TARGET -> "You are not the trade target."
            ACTIVE_PLAYER_CANNOT_SPY -> "The active player cannot spy."
            ALREADY_SPIED_THIS_TURN -> "You already spied this turn."
            CANNOT_SPY_WITHOUT_MONEY -> "You need at least one money card to spy."
            CANNOT_CATCH_WHILE_SPYING -> "You cannot catch a spy while spying."
            NOT_SPIED_UPON -> "You were not spied upon."
        }
}
