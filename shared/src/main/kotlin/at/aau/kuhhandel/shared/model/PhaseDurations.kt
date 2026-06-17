package at.aau.kuhhandel.shared.model

object PhaseDurations {
    const val PLAYER_CHOICE_MS = 15_000L
    const val AUCTION_BIDDING_MS = 5_000L
    const val AUCTIONEER_DECISION_MS = 5_000L
    const val AUCTION_PAYMENT_MS = 15_000L
    const val AUCTION_RESULT_MS = 5_000L
    const val TRADE_OFFER_MS = 15_000L
    const val TRADE_RESPONSE_MS = 15_000L
    const val TRADE_RESULT_MS = 10_000L

    const val SPY_WINDOW_MS = 1_000L

    const val DISCONNECTED_TURN_DURATION_MS = 5_000L
}
