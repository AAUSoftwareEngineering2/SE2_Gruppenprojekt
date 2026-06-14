package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.exception.GameException
import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.server.model.RoomActionResult
import at.aau.kuhhandel.server.persistence.GamePersistenceService
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GameErrorReason
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.PlayerNameRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Core domain orchestration layer for the server.
 */
@Service
class GameService(
    private val eventPublisher: ApplicationEventPublisher,
    private val persistenceService: GamePersistenceService? = null,
    private val gameSessionFactory: (String, String, String) -> GameSession = ::GameSession,
    // Used in tests
    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Stores all active game sessions by their 5-digit game id
    private val rooms: ConcurrentHashMap<String, SyncGameRoom> = ConcurrentHashMap()

    /**
     * Creates a new game with a unique 5-digit game id.
     */
    fun createGame(rawHostPlayerName: String): RoomActionResult {
        val gameId: String
        val playerId = generatePlayerId()
        val playerName = resolvePlayerName(rawHostPlayerName)
        val session: GameSession

        synchronized(rooms) {
            gameId = generateGameCode()
            session = gameSessionFactory(gameId, playerId, playerName)
            rooms[gameId] = SyncGameRoom(session)
        }

        persistSafely(session)
        return RoomActionResult(gameId, playerId, session.state)
    }

    /**
     * Returns a game session by its game id. Falls back to a persisted snapshot when no live
     * in-memory session exists, allowing reconnects after the original WebSocket closed.
     */
    fun getGame(gameId: String): GameSession? {
        rooms[gameId]?.session?.let { return it }
        val loadedState = persistenceService?.loadGameState(gameId) ?: return null
        val hostPlayer =
            loadedState.players.firstOrNull { it.id == loadedState.hostPlayerId }
                ?: loadedState.players.firstOrNull()
        val session =
            GameSession(
                gameId = gameId,
                hostPlayerId = hostPlayer?.id ?: "host",
                hostPlayerName = hostPlayer?.name ?: "host",
                initialState = loadedState,
            )
        rooms[gameId] = SyncGameRoom(session)
        // Restart the auction watcher when reviving an in-flight auction from disk — the
        // in-memory coroutine that originally guarded it is gone with the previous server life.
        if (loadedState.timerEnd != null) {
            schedulePhaseTimeout(gameId)
        }
        return session
    }

    /**
     * Removes the in-memory game session. The persisted snapshot is left intact so a reconnect can
     * reload it via [getGame]. Use [purgeGame] to wipe persistence as well.
     */
    fun removeGame(gameId: String) {
        val room = rooms.remove(gameId)
        room?.phaseTimerJob?.cancel()
        room?.spyTimerJob?.cancel()
    }

    /**
     * Removes both the in-memory session and the persisted snapshot for [gameId].
     */
    fun purgeGame(gameId: String) {
        removeGame(gameId)
        runCatching { persistenceService?.deleteGame(gameId) }
            .onFailure { logger.warn("Failed to purge persisted game $gameId", it) }
    }

    /**
     * Adds a player to a game.
     *
     * Fails with a client-facing error if the game does not exist.
     */
    suspend fun joinGame(
        gameId: String,
        rawPlayerName: String,
    ): RoomActionResult {
        val room =
            rooms[gameId]
                ?: throw GameException(GameErrorReason.GAME_NOT_FOUND)

        val playerId = generatePlayerId()
        val playerName = resolvePlayerName(rawPlayerName)

        room.mutex.withLock {
            val updatedState = room.session.addPlayer(playerId, playerName)
            persistSafely(room.session)
            return RoomActionResult(gameId, playerId, updatedState)
        }
    }

    /**
     * Removes a player from a game.
     *
     * Expects a valid [gameId].
     */
    suspend fun leaveGame(
        gameId: String,
        playerId: String,
    ): GameState {
        val room = fetchGameRoom(gameId)

        room.mutex.withLock {
            val updatedState = room.session.removePlayer(playerId)
            if (updatedState.players.isEmpty()) {
                purgeGame(gameId)
            } else {
                persistSafely(room.session)
            }
            return updatedState
        }
    }

    /**
     * Retrieves the state of the game with the given game ID if
     * the provided player ID corresponds to one of the players.
     *
     * Fails with a client-facing error if the game does not exist or if the player ID is invalid.
     */
    suspend fun getStateForReconnection(
        gameId: String,
        playerId: String,
    ): GameState {
        getGame(gameId)

        val room =
            rooms[gameId]
                ?: throw GameException(GameErrorReason.GAME_NOT_FOUND)

        room.mutex.withLock {
            if (!room.session.hasPlayer(playerId)) {
                throw GameException(GameErrorReason.PLAYER_NOT_IN_GAME)
            }

            return room.session.state
        }
    }

    /**
     * Starts an existing game.
     *
     * Expects a valid [gameId].
     */
    suspend fun startGame(
        gameId: String,
        actorId: String,
    ): GameState = executeAction(gameId) { session -> session.startGame(actorId) }

    /**
     * Starts an auction.
     *
     * Expects a valid [gameId].
     */
    suspend fun chooseAuction(
        gameId: String,
        actorId: String,
    ): GameState = executeAction(gameId) { session -> session.chooseAuction(actorId) }

    /**
     * Placed a bid on an ongoing auction.
     *
     * Expects a valid [gameId].
     */
    suspend fun placeBid(
        gameId: String,
        actorId: String,
        amount: Int,
    ): GameState = executeAction(gameId) { session -> session.placeBid(actorId, amount) }

    /**
     * Resolves the current auction phase, allowing the auctioneer to buy back the card or sell to the high bidder.
     *
     * Expects a valid [gameId].
     */
    suspend fun resolveAuction(
        gameId: String,
        actorId: String,
        auctioneerBuysCard: Boolean,
    ): GameState =
        executeAction(gameId) { session -> session.resolveAuction(actorId, auctioneerBuysCard) }

    /**
     * Starts a trade against an opponent.
     *
     * Expects a valid [gameId].
     */
    suspend fun chooseTrade(
        gameId: String,
        actorId: String,
        targetId: String,
        animalType: AnimalType,
    ): GameState =
        executeAction(gameId) { session ->
            session.chooseTrade(
                actorId,
                targetId,
                animalType,
            )
        }

    /**
     * Submits the money cards offered by the trade initiator.
     *
     * Expects a valid [gameId].
     */
    suspend fun submitTradeMoney(
        gameId: String,
        actorId: String,
        offeredMoneyCardIds: Set<String>,
    ): GameState =
        executeAction(gameId) { session ->
            session.submitTradeMoney(
                actorId,
                offeredMoneyCardIds,
            )
        }

    /**
     * Submits a response to a trade, with empty [counterOfferedMoneyCardIds]
     * representing blind trade acceptance.
     *
     * Expects a valid [gameId].
     */
    suspend fun respondToTrade(
        gameId: String,
        actorId: String,
        counterOfferedMoneyCardIds: Set<String>,
    ): GameState =
        executeAction(gameId) { session ->
            session.respondToTrade(
                actorId,
                counterOfferedMoneyCardIds,
            )
        }

    /**
     * Starts spying on a chosen target player.
     *
     * Expects a valid [gameId].
     */
    suspend fun spy(
        gameId: String,
        actorId: String,
        targetId: String,
    ): GameState = executeSpyAction(gameId) { session -> session.spy(actorId, targetId) }

    /**
     * Catches all players currently spying on the player.
     *
     * Expects a valid [gameId].
     */
    suspend fun catchSpy(
        gameId: String,
        actorId: String,
    ): GameState = executeSpyAction(gameId) { session -> session.catchSpy(actorId) }

    /**
     * Centralized helper that handles room fetching, mutex locking, state persistence,
     * and automatic timer updates for all game modifications.
     */
    private suspend inline fun executeAction(
        gameId: String,
        crossinline action: (GameSession) -> GameState,
    ): GameState {
        val room = fetchGameRoom(gameId)

        room.mutex.withLock {
            val newState = action(room.session)
            persistSafely(room.session)

            // Update the background job
            schedulePhaseTimeout(gameId)

            return newState
        }
    }

    /**
     * Schedules a background check to automatically advance
     * the game state when the current phase's timer expires.
     */
    private fun schedulePhaseTimeout(gameId: String) {
        val room = rooms[gameId] ?: return

        // Defensively cancel the previous timer coroutine job for this room to prevent leaks
        room.phaseTimerJob?.cancel()
        room.phaseTimerJob = null

        val timerEnd = room.session.state.timerEnd ?: return

        // Launch a fresh job tracking the current timeout window
        room.phaseTimerJob =
            serviceScope.launch {
                val now = System.currentTimeMillis()
                val delayDuration = timerEnd - now

                // Wait out the timer duration, plus a 100ms safety pad to avoid clock race conditions
                if (delayDuration > 0) {
                    delay(delayDuration + 100)
                }

                // Acquire the game session's mutex lock to safely advance the game
                room.mutex.withLock {
                    // Confirm the state has not been changed or updated while this routine was waiting
                    if (room.session.state.timerEnd == timerEnd) {
                        val updatedState = room.session.handleTimeoutExpiration()

                        persistSafely(room.session)

                        eventPublisher.publishEvent(GameStateChangedEvent(gameId, updatedState))

                        // If the next state also sets a timeout, recursively spin up the next handler
                        if (updatedState.timerEnd != null) {
                            schedulePhaseTimeout(gameId)
                        }
                    }
                }
            }
    }

    /**
     * Dedicated helper for spy mutations. Reuses the core execution mechanics
     * but ensures the independent spy timer tracking is recalculated.
     */
    private suspend inline fun executeSpyAction(
        gameId: String,
        crossinline action: (GameSession) -> GameState,
    ): GameState {
        val room = fetchGameRoom(gameId)

        room.mutex.withLock {
            val newState = action(room.session)
            persistSafely(room.session)

            // Maintain standard phase timeout checks
            schedulePhaseTimeout(gameId)

            // Explicitly recalculate or clear the spy timer loop
            scheduleSpyExpirationWatcher(gameId)

            return newState
        }
    }

    /**
     * Schedules an independent background check to automatically clear
     * spy actions from the state when their tracking windows expire.
     */
    private fun scheduleSpyExpirationWatcher(gameId: String) {
        val room = rooms[gameId] ?: return

        // Cancel the old job handle first to avoid leaking coroutines
        room.spyTimerJob?.cancel()
        room.spyTimerJob = null

        // If no spies remain, exit
        val nextExpirationTime = room.session.getEarliestSpyExpiration() ?: return

        room.spyTimerJob =
            serviceScope.launch {
                val delayDuration = nextExpirationTime - System.currentTimeMillis()
                if (delayDuration > 0) {
                    delay(delayDuration + 100)
                }

                room.mutex.withLock {
                    val oldState = room.session.state
                    val newState = room.session.clearExpiredSpies()

                    // If a new GameState instance was copied, propagate the changes
                    if (newState !== oldState) {
                        persistSafely(room.session)

                        eventPublisher.publishEvent(GameStateChangedEvent(gameId, newState))

                        // Reschedule for the next spy deadline if any remain
                        if (room.session.hasActiveSpies()) {
                            scheduleSpyExpirationWatcher(gameId)
                        }
                    }
                }
            }
    }

    /**
     * Best-effort persistence — failures are logged but never propagated so that in-memory game
     * play continues to work if the database is briefly unavailable.
     */
    private fun persistSafely(session: GameSession) {
        val service = persistenceService ?: return
        runCatching { service.saveGameState(session.gameId, session.state) }
            .onFailure { logger.warn("Failed to persist game ${session.gameId}", it) }
    }

    /**
     * Resolves a raw player name, throwing an exception if the name is invalid.
     */
    private fun resolvePlayerName(rawName: String): String {
        val trimmed = rawName.trim()
        if (!PlayerNameRules.isValid(trimmed)) {
            throw GameException(GameErrorReason.INVALID_PLAYER_NAME)
        }
        return trimmed
    }

    /**
     * Generates a unique 5-digit game code.
     */
    private fun generateGameCode(): String {
        var code: String

        do {
            code = Random.nextInt(10000, 100000).toString()
        } while (rooms.containsKey(code) || persistenceService?.existsGame(code) == true)

        return code
    }

    /**
     * Generates a unique player identifier.
     *
     * The map [at.aau.kuhhandel.server.websocket.ConnectionRegistry.reconnectTokens] relies
     * on the global uniqueness of player IDs to map them directly to reconnection tokens
     * without requiring composite keys that include game IDs.
     */
    private fun generatePlayerId(): String = UUID.randomUUID().toString()

    /**
     * Asserts that a game session exists in the active room map.
     *
     * @throws IllegalStateException If the game service is out of sync with the caller.
     */
    private fun fetchGameRoom(gameId: String): SyncGameRoom =
        checkNotNull(rooms[gameId]) {
            "Game registry does not contain game session $gameId"
        }
}

/**
 * Wrapper coupling a running [GameSession] with its atomic
 * execution [Mutex] and background timeout task.
 */
private class SyncGameRoom(
    val session: GameSession,
    val mutex: Mutex = Mutex(),
    var phaseTimerJob: Job? = null,
    var spyTimerJob: Job? = null,
)
