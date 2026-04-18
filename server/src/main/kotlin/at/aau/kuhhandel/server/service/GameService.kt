package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class GameService {
    // Stores all active game sessions by their 5-digit game id
    private val sessions: MutableMap<String, GameSession> = mutableMapOf()

    /**
     * Creates a new game with a unique 5-digit game id.
     */
    fun createGame(playerId: String): GameSession {
        val gameId = generateGameCode()
        val session = GameSession(gameId, playerId)

        sessions[gameId] = session
        return session
    }

    /**
     * Returns a game session by its game id.
     */
    fun getGame(gameId: String): GameSession? = sessions[gameId]

    /**
     * Starts an existing game.
     */
    fun startGame(gameId: String): GameState? {
        val session = sessions[gameId] ?: return null
        return session.startGame()
    }

    /**
     * Reveals the next card for an existing game.
     */
    fun revealNextCard(gameId: String): GameState? {
        val session = sessions[gameId] ?: return null
        val updatedState = session.revealNextCard()

        if (updatedState.phase == GamePhase.FINISHED) {
            // Optional cleanup later:
            // sessions.remove(gameId)
        }

        return updatedState
    }

    /**
     * Generates a unique 5-digit game code.
     */
    private fun generateGameCode(): String {
        var code: String

        do {
            code = Random.nextInt(10000, 100000).toString()
        } while (sessions.containsKey(code))

        return code
    }
}
