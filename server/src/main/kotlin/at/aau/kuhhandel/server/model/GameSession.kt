package at.aau.kuhhandel.server.model

import at.aau.kuhhandel.server.service.GameCommand
import at.aau.kuhhandel.server.service.GameStateMachine
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.PlayerState

class GameSession(
    val gameId: String,
    val playerId: String,
    private val stateMachine: GameStateMachine = GameStateMachine(),
) {
    // Each session manages its own current game state
    // Server Side TODO: Handle joining of multiple players instead of hardcoding one player
    var gameState: GameState =
        GameState(players = listOf(PlayerState(id = playerId, name = playerId)))
        private set

    /**
     * Starts a game with a simple initial deck.
     */
    fun startGame(): GameState {
        gameState = stateMachine.apply(gameState, GameCommand.StartGame)
        return gameState
    }

    /**
     * Reveals the next card from the deck.
     */
    fun revealNextCard(): GameState {
        gameState = stateMachine.apply(gameState, GameCommand.RevealCard)
        return gameState
    }

    // Server Side TODO: Implement interactive methods to be called by GameService:
    // fun placeBid(playerId: String, amount: Int) {
    //    gameState = stateMachine.apply(gameState, GameCommand.PlaceBid(playerId, amount))
    // }

    // fun resolveAuction(buyBack: Boolean) { ... }

    // fun respondToTrade(accepted: Boolean, money: List<MoneyCard>) { ... }

    fun chooseAuction(): GameState {
        gameState = stateMachine.apply(gameState, GameCommand.ChooseAuction)
        return gameState
    }

    fun chooseTrade(challengedPlayerId: String): GameState {
        gameState =
            stateMachine.apply(
                gameState,
                GameCommand.ChooseTrade(challengedPlayerId),
            )
        return gameState
    }

    fun finishRound(): GameState {
        gameState = stateMachine.apply(gameState, GameCommand.FinishRound)
        return gameState
    }
}
