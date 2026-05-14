package at.aau.kuhhandel.shared.enums

import kotlinx.serialization.Serializable

@Serializable
enum class GameErrorReason {
    // Connection infrastructure
    SESSION_ALREADY_BOUND_TO_GAME,
    SESSION_NOT_BOUND_TO_GAME,
    SESSION_NOT_BOUND_TO_PLAYER,
    GAME_NOT_FOUND,

    // Game flow
    INVALID_GAME_PHASE,
    UNKNOWN_PLAYER,
    DUPLICATE_PLAYER,
    ROOM_FULL,
    NOT_ROOM_HOST,
    NOT_YOUR_TURN,
    DECK_EMPTY,
    OWN_AUCTION,
    BID_TOO_LOW,
    CHALLENGING_SELF,
    UNKNOWN_CHALLENGED_PLAYER,
    OFFER_EMPTY,
}
