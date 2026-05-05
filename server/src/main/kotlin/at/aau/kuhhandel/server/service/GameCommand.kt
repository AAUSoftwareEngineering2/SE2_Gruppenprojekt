package at.aau.kuhhandel.server.service

/**
 * Server-side commands that move a game from one deterministic state to the next.
 */
sealed interface GameCommand {
    data object StartGame : GameCommand

    data object RevealCard : GameCommand

    data object ChooseAuction : GameCommand

    data class PlaceBid(
        val bidderId: String,
        val amount: Int,
    ) : GameCommand

    data object CloseAuction : GameCommand

    data class ResolveAuction(
        val auctioneerBuysCard: Boolean,
    ) : GameCommand

    data class ChooseTrade(
        val challengedPlayerId: String,
    ) : GameCommand

    data class OfferTrade(
        val offeredMoneyCardIds: List<String>,
    ) : GameCommand

    data class RespondToTrade(
        val respondingPlayerId: String,
        val accepted: Boolean,
    ) : GameCommand

    data object FinishRound : GameCommand
}
