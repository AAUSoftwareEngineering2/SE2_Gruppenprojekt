package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.server.model.RoomActionResult
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Service
class GameService(
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    // Stores all active game sessions by their 5-digit game id
    private val sessions: MutableMap<String, GameSession> = ConcurrentHashMap()

    /**
     * Creates a new game with a unique 5-digit game id.
     */
    fun createGame(hostPlayerName: String): RoomActionResult {
        val gameId = generateGameCode()
        val playerId = UUID.randomUUID().toString()
        val session = GameSession(gameId, playerId, hostPlayerName)

        sessions[gameId] = session
        return RoomActionResult(gameId, playerId, session.gameState)
    }

    /**
     * Returns a game session by its game id.
     */
    fun getGame(gameId: String): GameSession? = sessions[gameId.trim()]

    /**
     * Removes the game session with the given game id.
     */
    fun removeGame(gameId: String) {
        sessions.remove(gameId.trim())
    }

    /**
     * Starts an existing game.
     */
    fun startGame(gameId: String): GameState? {
        val session = sessions[gameId.trim()] ?: return null
        return session.startGame()
    }

    /**
     * Adds a player to a game
     */
    fun joinGame(
        gameId: String,
        playerName: String,
        requestId: String? = null,
    ): RoomActionResult? {
        val session = sessions[gameId.trim()] ?: return null
        val playerId = UUID.randomUUID().toString()

        val updatedState = session.addPlayer(playerId, playerName)

        eventPublisher.publishEvent(GameStateChangedEvent(gameId.trim(), updatedState, requestId))

        return RoomActionResult(gameId.trim(), playerId, updatedState)
    }

    /**
     * Removes a player from a game
     */
    fun leaveGame(
        gameId: String,
        playerId: String,
        requestId: String? = null,
    ): GameState? {
        val session = sessions[gameId.trim()] ?: return null

        val updatedState = session.removePlayer(playerId)

        if (updatedState.players.isEmpty()) {
            sessions.remove(gameId.trim())
        } else {
            eventPublisher.publishEvent(GameStateChangedEvent(gameId.trim(), updatedState, requestId))
        }

        return updatedState
    }

    /**
     * Reveals the next card for an existing game.
     */
    fun revealNextCard(gameId: String): GameState? {
        val session = sessions[gameId.trim()] ?: return null
        val updatedState = session.revealNextCard()

        if (updatedState.phase == GamePhase.FINISHED) {
            // Optional cleanup later:
            // sessions.remove(gameId)
        }

        return updatedState
    }

    fun chooseAuction(gameId: String): GameState? {
        val session = sessions[gameId.trim()] ?: return null
        val state = session.chooseAuction()
        scheduleAutoClose(gameId.trim())
        return state
    }

    fun placeBid(
        gameId: String,
        bidderId: String,
        amount: Int,
    ): GameState? {
        val session = sessions[gameId.trim()] ?: return null
        val state = session.placeBid(bidderId, amount)
        scheduleAutoClose(gameId.trim())
        return state
    }

    private fun scheduleAutoClose(gameId: String) {
        val session = sessions[gameId.trim()] ?: return
        val endTime = session.gameState.auctionState?.timerEndTime ?: return

        serviceScope.launch {
            delay(5100) // Wait slightly longer than the timer to be safe
            val currentSession = sessions[gameId.trim()] ?: return@launch
            // If the timerEndTime is still the same, it means no new bid happened
            if (currentSession.gameState.auctionState?.timerEndTime == endTime &&
                currentSession.gameState.auctionState?.isClosed == false
            ) {
                val updatedState = closeAuction(gameId.trim())
                if (updatedState != null) {
                    eventPublisher.publishEvent(GameStateChangedEvent(gameId.trim(), updatedState))
                }
            }
        }
    }

    fun closeAuction(gameId: String): GameState? {
        val session = sessions[gameId.trim()] ?: return null
        return session.closeAuction()
    }

    fun resolveAuction(
        gameId: String,
        auctioneerBuysCard: Boolean,
    ): GameState? {
        val session = sessions[gameId.trim()] ?: return null
        return session.resolveAuction(auctioneerBuysCard)
    }

    fun chooseTrade(
        gameId: String,
        challengedPlayerId: String,
        animalType: at.aau.kuhhandel.shared.enums.AnimalType,
        offeredMoneyCardIds: List<String> = emptyList(),
    ): GameState? {
        val session = sessions[gameId.trim()] ?: return null
        return session.chooseTrade(challengedPlayerId, animalType, offeredMoneyCardIds)
    }

    fun respondToTrade(
        gameId: String,
        respondingPlayerId: String,
        acceptsOffer: Boolean,
        counterOfferedMoneyCardIds: List<String> = emptyList(),
    ): GameState? {
        val session = sessions[gameId.trim()] ?: return null
        return session.respondToTrade(
            respondingPlayerId = respondingPlayerId,
            acceptsOffer = acceptsOffer,
            counterOfferedMoneyCardIds = counterOfferedMoneyCardIds,
        )
    }

    fun offerTrade(
        gameId: String,
        offeredMoneyCardIds: List<String>,
    ): GameState? {
        val session = sessions[gameId.trim()] ?: return null
        return session.offerTrade(offeredMoneyCardIds)
    }

    fun finishRound(gameId: String): GameState? {
        val session = sessions[gameId.trim()] ?: return null
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
