package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class GameService(
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val serviceScope = CoroutineScope(Dispatchers.Default)

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
        val state = session.chooseAuction()
        scheduleAutoClose(gameId)
        return state
    }

    fun placeBid(
        gameId: String,
        bidderId: String,
        amount: Int,
    ): GameState? {
        val session = sessions[gameId] ?: return null
        val state = session.placeBid(bidderId, amount)
        scheduleAutoClose(gameId)
        return state
    }

    private fun scheduleAutoClose(gameId: String) {
        val session = sessions[gameId] ?: return
        val endTime = session.gameState.auctionState?.timerEndTime ?: return

        serviceScope.launch {
            delay(5100) // Wait slightly longer than the timer to be safe
            val currentSession = sessions[gameId] ?: return@launch
            // If the timerEndTime is still the same, it means no new bid happened
            if (currentSession.gameState.auctionState?.timerEndTime == endTime &&
                currentSession.gameState.auctionState?.isClosed == false
            ) {
                val updatedState = closeAuction(gameId)
                if (updatedState != null) {
                    eventPublisher.publishEvent(GameStateChangedEvent(gameId, updatedState))
                }
            }
        }
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
