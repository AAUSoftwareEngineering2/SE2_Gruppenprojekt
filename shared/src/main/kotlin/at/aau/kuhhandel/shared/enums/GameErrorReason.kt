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
    SESSION_ALREADY_BOUND_TO_GAME,
    SESSION_NOT_BOUND_TO_GAME,
    SESSION_NOT_BOUND_TO_PLAYER,
    GAME_NOT_FOUND,
    PLAYER_NOT_IN_GAME,

    // Game flow
    INVALID_PHASE,
    UNKNOWN_ACTOR,
    NOT_YOUR_TURN,

    ALREADY_IN_ROOM,
    NOT_ENOUGH_PLAYERS,
    ROOM_FULL,
    NOT_HOST,

    DECK_EMPTY,
    OWN_AUCTION,
    BID_TOO_LOW,
    NOT_ENOUGH_MONEY,
    NOT_AUCTIONEER,

    UNKNOWN_TRADE_TARGET,
    TARGETING_SELF,
    INITIATOR_MISSING_ANIMAL,
    TARGET_MISSING_ANIMAL,
    OFFER_EMPTY,
    NOT_OWNED_MONEY_CARDS,
    NOT_TRADE_TARGET,
    PLAYER_EXCLUDED_FROM_AUCTION,
}
