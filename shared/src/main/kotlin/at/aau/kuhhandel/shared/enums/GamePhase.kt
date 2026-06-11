package at.aau.kuhhandel.shared.enums

import kotlinx.serialization.Serializable

@Serializable
enum class GamePhase {
    NOT_STARTED,

    PLAYER_CHOICE,

    AUCTION_BIDDING,
    AUCTIONEER_DECISION,
    AUCTION_RESULT,

    TRADE_OFFER,
    TRADE_RESPONSE,
    TRADE_RESULT,

    FINISHED,
}
