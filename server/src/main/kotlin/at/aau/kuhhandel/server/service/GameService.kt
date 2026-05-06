package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.random.Random

@Service
class GameService {
    // Stores all active game sessions by their 5-digit game id
    private val sessions: MutableMap<String, GameSession> = mutableMapOf()

    /**
     * Creates a new game with a unique 5-digit game id.
     */
    fun createGame(hostPlayerName: String): GameSession {
        val gameId = generateGameCode()
        val playerId = UUID.randomUUID().toString()
        val session = GameSession(gameId, playerId, hostPlayerName)

        sessions[gameId] = session
        return session
    }

    /**
     * Returns a game session by its game id.
     */
    fun getGame(gameId: String): GameSession? = sessions[gameId]

    /**
     * Removes the game session with the given game id.
     */
    fun removeGame(gameId: String) {
        sessions.remove(gameId)
    }

    /**
     * Starts an existing game.
     */
    fun startGame(gameId: String): GameState? {
        val session = sessions[gameId] ?: return null
        return session.startGame()
    }

    /**
     * Adds a player to a game
     */
    fun joinGame(
        gameId: String,
        playerName: String,
    ): GameState? {
        val session = sessions[gameId] ?: return null
        val playerId = UUID.randomUUID().toString()
        return session.addPlayer(playerId, playerName)
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

    fun chooseAuction(gameId: String): GameState? {
        val session = sessions[gameId] ?: return null
        return session.chooseAuction()
    }

    fun placeBid(
        gameId: String,
        bidderId: String,
        amount: Int,
    ): GameState? {
        val session = sessions[gameId] ?: return null
        return session.placeBid(bidderId, amount)
    }

    fun closeAuction(gameId: String): GameState? {
        val session = sessions[gameId] ?: return null
        return session.closeAuction()
    }

    fun resolveAuction(
        gameId: String,
        auctioneerBuysCard: Boolean,
    ): GameState? {
        val session = sessions[gameId] ?: return null
        return session.resolveAuction(auctioneerBuysCard)
    }

    fun chooseTrade(
        gameId: String,
        challengedPlayerId: String,
    ): GameState? {
        val session = sessions[gameId] ?: return null
        return session.chooseTrade(challengedPlayerId)
    }

    fun finishRound(gameId: String): GameState? {
        val session = sessions[gameId] ?: return null
        return session.finishRound()
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
