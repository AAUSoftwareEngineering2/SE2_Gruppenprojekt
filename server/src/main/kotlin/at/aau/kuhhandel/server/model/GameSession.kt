package at.aau.kuhhandel.server.model

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AnimalDeck
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.PlayerState

class GameSession(
    val gameId: String,
    val playerId: String,
) {
    // Each session manages its own current game state
    var gameState: GameState =
        GameState(players = listOf(PlayerState(id = playerId, name = playerId)))
        private set

    /**
     * Starts a game with a simple initial deck.
     */
    fun startGame(): GameState {
        val initialDeck =
            AnimalDeck(
                listOf(
                    AnimalCard(id = "1", type = AnimalType.COW),
                    AnimalCard(id = "2", type = AnimalType.DOG),
                    AnimalCard(id = "3", type = AnimalType.CAT),
                ),
            )

        gameState =
            gameState.copy(
                phase = GamePhase.PLAYER_TURN,
                deck = initialDeck,
                currentFaceUpCard = null,
                currentPlayerIndex = 0,
                auctionState = null,
                tradeState = null,
            )

        return gameState
    }

    /**
     * Reveals the next card from the deck.
     */
    fun revealNextCard(): GameState {
        val currentState = gameState

        if (currentState.deck.isEmpty()) {
            gameState =
                currentState.copy(
                    phase = GamePhase.FINISHED,
                    currentFaceUpCard = null,
                    auctionState = null,
                    tradeState = null,
                )
            return gameState
        }

        val (nextCard, updatedDeck) = currentState.deck.drawTopCard()

        gameState =
            currentState.copy(
                deck = updatedDeck,
                currentFaceUpCard = nextCard,
                phase = GamePhase.PLAYER_TURN,
                auctionState = null,
                tradeState = null,
            )

        return gameState
    }
}
