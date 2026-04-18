package at.aau.kuhhandel.shared.enums

import kotlinx.serialization.Serializable

@Serializable
enum class GamePhase {
    NOT_STARTED,

    PLAYER_TURN,

    AUCTION,
    TRADE,

    ROUND_END,

    FINISHED,
}
