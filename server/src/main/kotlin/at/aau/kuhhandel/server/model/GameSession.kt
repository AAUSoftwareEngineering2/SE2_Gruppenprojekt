package at.aau.kuhhandel.server.model

import at.aau.kuhhandel.server.service.GameCommand
import at.aau.kuhhandel.server.service.GameStateMachine
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.PlayerState

class GameSession(
    val gameId: String,
    hostPlayerId: String,
    hostPlayerName: String,
    private val stateMachine: GameStateMachine = GameStateMachine(),
) {
    // Each session manages its own current game state
    // Server Side TODO: Handle joining of multiple players instead of hardcoding one player
    var gameState: GameState =
        GameState(players = listOf(PlayerState(id = hostPlayerId, name = hostPlayerName)))
        private set

    /**
     * Starts a game with a simple initial deck.
     */
    fun startGame(): GameState {
        gameState = stateMachine.apply(gameState, GameCommand.StartGame)
        return gameState
    }

    /**
     * Adds a player to the game session
     */
    fun addPlayer(
        playerId: String,
        playerName: String,
    ): GameState {
        gameState = stateMachine.apply(gameState, GameCommand.AddPlayer(playerId, playerName))
        return gameState
    }

    /**
     * Removes a player from the game session
     */
    fun removePlayer(playerId: String): GameState {
        gameState = stateMachine.apply(gameState, GameCommand.RemovePlayer(playerId))
        return gameState
    }

    /**
     * Reveals the next card from the deck.
     */
    fun revealNextCard(): GameState {
        gameState = stateMachine.apply(gameState, GameCommand.RevealCard)
        return gameState
    }

    fun chooseAuction(): GameState {
        gameState = stateMachine.apply(gameState, GameCommand.ChooseAuction)
        return gameState
    }

    fun placeBid(
        bidderId: String,
        amount: Int,
    ): GameState {
        gameState =
            stateMachine.apply(
                gameState,
                GameCommand.PlaceBid(bidderId, amount),
            )
        return gameState
    }

    fun closeAuction(): GameState {
        gameState = stateMachine.apply(gameState, GameCommand.CloseAuction)
        return gameState
    }

    fun resolveAuction(auctioneerBuysCard: Boolean): GameState {
        gameState =
            stateMachine.apply(
                gameState,
                GameCommand.ResolveAuction(auctioneerBuysCard),
            )
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

    fun offerTrade(offeredMoneyCardIds: List<String>): GameState {
        gameState =
            stateMachine.apply(
                gameState,
                GameCommand.OfferTrade(offeredMoneyCardIds),
            )
        return gameState
    }

    fun respondToTrade(
        respondingPlayerId: String,
        accepted: Boolean,
    ): GameState {
        gameState =
            stateMachine.apply(
                gameState,
                GameCommand.RespondToTrade(respondingPlayerId, accepted),
            )
        return gameState
    }

    fun finishRound(): GameState {
        gameState = stateMachine.apply(gameState, GameCommand.FinishRound)
        return gameState
    }
}
