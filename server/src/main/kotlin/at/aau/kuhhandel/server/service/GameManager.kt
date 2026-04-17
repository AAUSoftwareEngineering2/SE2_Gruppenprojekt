package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AnimalDeck
import at.aau.kuhhandel.shared.model.GameState
import java.util.UUID

/**
 * Manages the game sessions (minimal logic).
 */
class GameManager {
    // Stores all active sessions
    private val sessions: MutableMap<String, GameSession> = mutableMapOf()

    /**
     * Creates a simple test game with a small deck.
     */
    fun createTestGame(): GameSession {
        val testDeck =
            AnimalDeck(
                mutableListOf(
                    AnimalCard(id = "1", type = AnimalType.COW),
                    AnimalCard(id = "2", type = AnimalType.DOG),
                    AnimalCard(id = "3", type = AnimalType.CAT),
                ),
            )

        val gameState =
            GameState(
                phase = GamePhase.PLAYER_TURN,
                deck = testDeck,
                currentFaceUpCard = null,
                currentPlayerIndex = 0,
                players = emptyList(),
            )

        val session =
            GameSession(
                sessionId = UUID.randomUUID().toString(),
                gameState = gameState,
            )

        sessions[session.sessionId] = session
        return session
    }

    /**
     * Returns a session by ID.
     */
    fun getSession(sessionId: String): GameSession? = sessions[sessionId]

    /**
     * Reveals the next card in the deck.
     */
    fun revealNextCard(sessionId: String): GameState? {
        val session = sessions[sessionId] ?: return null
        val currentState = session.gameState

        // If the deck is already empty, finish the game.
        if (currentState.deck.isEmpty()) {
            val finishedState =
                currentState.copy(
                    phase = GamePhase.FINISHED,
                    currentFaceUpCard = null,
                )

            sessions[sessionId] = session.copy(gameState = finishedState)
            return finishedState
        }

        // Draw the next card from the deck.
        val nextCard = currentState.deck.drawTopCard()

        val updatedState =
            currentState.copy(
                currentFaceUpCard = nextCard,
                phase = GamePhase.PLAYER_TURN,
            )

        sessions[sessionId] = session.copy(gameState = updatedState)
        return updatedState
    }
}
