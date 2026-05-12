package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.server.persistence.GamePersistenceService
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class GameService(
    private val eventPublisher: ApplicationEventPublisher,
    private val persistenceService: GamePersistenceService? = null,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
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
        persistSafely(session)
        return session
    }

    /**
     * Returns a game session by its game id. Falls back to a persisted snapshot when no live
     * in-memory session exists, allowing reconnects after the original WebSocket closed.
     */
    fun getGame(gameId: String): GameSession? {
        sessions[gameId]?.let { return it }
        val loadedState = persistenceService?.loadGameState(gameId) ?: return null
        val playerId = loadedState.players.firstOrNull()?.id ?: "player-1"
        val session =
            GameSession(
                gameId = gameId,
                playerId = playerId,
                initialState = loadedState,
            )
        sessions[gameId] = session
        return session
    }

    /**
     * Removes the in-memory game session. The persisted snapshot is left intact so a reconnect can
     * reload it via [getGame]. Use [purgeGame] to wipe persistence as well.
     */
    fun removeGame(gameId: String) {
        sessions.remove(gameId)
    }

    /**
     * Removes both the in-memory session and the persisted snapshot for [gameId].
     */
    fun purgeGame(gameId: String) {
        sessions.remove(gameId)
        runCatching { persistenceService?.deleteGame(gameId) }
            .onFailure { logger.warn("Failed to purge persisted game $gameId", it) }
    }

    /**
     * Starts an existing game.
     */
    fun startGame(gameId: String): GameState? {
        val session = sessions[gameId] ?: return null
        val state = session.startGame()
        persistSafely(session)
        return state
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

        persistSafely(session)
        return updatedState
    }

    fun chooseAuction(gameId: String): GameState? {
        val session = sessions[gameId] ?: return null
        val state = session.chooseAuction()
        persistSafely(session)
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
        persistSafely(session)
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
        val state = session.closeAuction()
        persistSafely(session)
        return state
    }

    fun resolveAuction(
        gameId: String,
        auctioneerBuysCard: Boolean,
    ): GameState? {
        val session = sessions[gameId] ?: return null
        val state = session.resolveAuction(auctioneerBuysCard)
        persistSafely(session)
        return state
    }

    fun chooseTrade(
        gameId: String,
        challengedPlayerId: String,
        offeredMoneyCardIds: List<String> = emptyList(),
    ): GameState? {
        val session = sessions[gameId] ?: return null
        val state = session.chooseTrade(challengedPlayerId, offeredMoneyCardIds)
        persistSafely(session)
        return state
    }

    fun respondToTrade(
        gameId: String,
        respondingPlayerId: String,
        acceptsOffer: Boolean,
        counterOfferedMoneyCardIds: List<String> = emptyList(),
    ): GameState? {
        val session = sessions[gameId] ?: return null
        val state =
            session.respondToTrade(
                respondingPlayerId = respondingPlayerId,
                acceptsOffer = acceptsOffer,
                counterOfferedMoneyCardIds = counterOfferedMoneyCardIds,
            )
        persistSafely(session)
        return state
    }

    fun offerTrade(
        gameId: String,
        offeredMoneyCardIds: List<String>,
    ): GameState? {
        val session = sessions[gameId] ?: return null
        val state = session.offerTrade(offeredMoneyCardIds)
        persistSafely(session)
        return state
    }

    fun finishRound(gameId: String): GameState? {
        val session = sessions[gameId] ?: return null
        val state = session.finishRound()
        persistSafely(session)
        return state
    }

    /**
     * Best-effort persistence — failures are logged but never propagated so that in-memory game
     * play continues to work if the database is briefly unavailable.
     */
    private fun persistSafely(session: GameSession) {
        val service = persistenceService ?: return
        runCatching { service.saveGameState(session.gameId, session.gameState) }
            .onFailure { logger.warn("Failed to persist game ${session.gameId}", it) }
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
