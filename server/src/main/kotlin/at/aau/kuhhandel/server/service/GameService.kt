package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.server.model.RoomActionResult
import at.aau.kuhhandel.shared.enums.AnimalType
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
    private val gameSessionFactory: (String, String, String) -> GameSession = ::GameSession,
) {
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    // Stores all active game sessions by their 5-digit game id
    private val sessions: ConcurrentHashMap<String, GameSession> = ConcurrentHashMap()

    /**
     * Creates a new game with a unique 5-digit game id.
     */
    fun createGame(hostPlayerName: String): RoomActionResult {
        val gameId = generateGameCode()
        val playerId = UUID.randomUUID().toString()
        val session = gameSessionFactory(gameId, playerId, hostPlayerName)

        sessions[gameId] = session
        return RoomActionResult(gameId, playerId, session.state)
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
    fun startGame(
        gameId: String,
        actorId: String,
    ): GameState? {
        val session = sessions[gameId] ?: return null
        return session.startGame(actorId)
    }

    /**
     * Adds a player to a game
     */
    fun joinGame(
        gameId: String,
        playerName: String,
    ): RoomActionResult? {
        val session = sessions[gameId] ?: return null
        val playerId = UUID.randomUUID().toString()

        val updatedState = session.addPlayer(playerId, playerName)

        return RoomActionResult(gameId, playerId, updatedState)
    }

    /**
     * Removes a player from a game
     */
    fun leaveGame(
        gameId: String,
        playerId: String,
    ): GameState? {
        val session = sessions[gameId] ?: return null

        val updatedState = session.removePlayer(playerId)

        if (updatedState.players.isEmpty()) sessions.remove(gameId)

        return updatedState
    }

    fun chooseAuction(
        gameId: String,
        actorId: String,
    ): GameState? {
        val session = sessions[gameId] ?: return null
        val state = session.chooseAuction(actorId)
        scheduleAutoClose(gameId)
        return state
    }

    fun placeBid(
        gameId: String,
        actorId: String,
        amount: Int,
    ): GameState? {
        val session = sessions[gameId] ?: return null
        val state = session.placeBid(actorId, amount)
        scheduleAutoClose(gameId)
        return state
    }

    private fun scheduleAutoClose(gameId: String) {
        val session = sessions[gameId] ?: return
        val endTime = session.state.auctionState?.timerEndTime ?: return

        serviceScope.launch {
            delay(5100) // Wait slightly longer than the timer to be safe
            val currentSession = sessions[gameId] ?: return@launch
            // If the timerEndTime is still the same, it means no new bid happened
            if (currentSession.state.auctionState?.timerEndTime == endTime) {
                val updatedState = closeAuctionAfterTimeout(gameId)
                if (updatedState != null) {
                    eventPublisher.publishEvent(GameStateChangedEvent(gameId, updatedState))
                }
            }
        }
    }

    fun closeAuctionAfterTimeout(gameId: String): GameState? {
        val session = sessions[gameId] ?: return null
        return session.closeAuctionAfterTimeout()
    }

    fun resolveAuction(
        gameId: String,
        actorId: String,
        auctioneerBuysCard: Boolean,
    ): GameState? {
        val session = sessions[gameId] ?: return null
        return session.resolveAuction(actorId, auctioneerBuysCard)
    }

    fun chooseTrade(
        gameId: String,
        actorId: String,
        targetId: String,
        animalType: AnimalType,
        offeredMoneyCardIds: Set<String>,
    ): GameState? {
        val session = sessions[gameId] ?: return null
        return session.chooseTrade(
            actorId = actorId,
            targetId = targetId,
            animalType = animalType,
            offeredMoneyCardIds = offeredMoneyCardIds,
        )
    }

    fun respondToTrade(
        gameId: String,
        actorId: String,
        counterOfferedMoneyCardIds: Set<String>,
    ): GameState? {
        val session = sessions[gameId] ?: return null
        return session.respondToTrade(
            actorId = actorId,
            counterOfferedMoneyCardIds = counterOfferedMoneyCardIds,
        )
    }

    fun finishTradeReveal(
        gameId: String,
        actorId: String,
    ): GameState? {
        val session = sessions[gameId] ?: return null
        return session.endTradeReveal()
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
