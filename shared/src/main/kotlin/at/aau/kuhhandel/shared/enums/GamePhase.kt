package at.aau.kuhhandel.shared.enums

import kotlinx.serialization.Serializable

@Serializable
enum class GamePhase {
    NOT_STARTED,

    PLAYER_CHOICE,

    AUCTION_BIDDING,
    AUCTION_RESOLUTION,

    TRADE_OFFER,
    TRADE_RESPONSE,
    TRADE_REVEAL,

    FINISHED,
}
