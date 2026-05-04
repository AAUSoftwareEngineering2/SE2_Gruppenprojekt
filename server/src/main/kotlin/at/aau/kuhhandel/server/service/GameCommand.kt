package at.aau.kuhhandel.server.service

/**
 * Server-side commands that move a game from one deterministic state to the next.
 */
sealed interface GameCommand {
    data object StartGame : GameCommand

    data object RevealCard : GameCommand

    data object ChooseAuction : GameCommand

    data class ChooseTrade(
        val challengedPlayerId: String,
    ) : GameCommand

    data object FinishRound : GameCommand

    // Server Side TODO: Add commands for the interactive parts of the phases:
    // - PlaceBid(playerId: String, amount: Int)
    // - ResolveAuction(buyBack: Boolean)
    // - RespondToTrade(accepted: Boolean, moneyCards: List<MoneyCard>?)
}
