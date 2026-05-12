package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.exception.GameException
import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.server.model.RoomActionResult
import at.aau.kuhhandel.server.persistence.GamePersistenceService
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GameErrorReason
import at.aau.kuhhandel.shared.model.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    fun createGame(hostPlayerName: String): RoomActionResult {
        val gameId: String
        val playerId = UUID.randomUUID().toString()
        val session: GameSession

        synchronized(rooms) {
            gameId = generateGameCode()
            session = gameSessionFactory(gameId, playerId, hostPlayerName)
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
        if (loadedState.auctionState != null) {
            scheduleAuctionAutoClose(gameId)
        }
        return session
    }

    /**
     * Removes the in-memory game session. The persisted snapshot is left intact so a reconnect can
     * reload it via [getGame]. Use [purgeGame] to wipe persistence as well.
     */
    fun removeGame(gameId: String) {
        rooms.remove(gameId)
    }

    /**
     * Removes both the in-memory session and the persisted snapshot for [gameId].
     */
    fun purgeGame(gameId: String) {
        rooms.remove(gameId)
        runCatching { persistenceService?.deleteGame(gameId) }
            .onFailure { logger.warn("Failed to purge persisted game $gameId", it) }
    }

    /**
     * Starts an existing game.
     */
    suspend fun startGame(
        gameId: String,
        actorId: String,
    ): GameState {
        val room = fetchGameRoom(gameId)

        room.mutex.withLock {
            val state = room.session.startGame(actorId)
            persistSafely(room.session)
            return state
        }
    }

    /**
     * Adds a player to a game
     */
    suspend fun joinGame(
        gameId: String,
        playerName: String,
    ): RoomActionResult {
        val room =
            rooms[gameId]
                ?: throw GameException(GameErrorReason.GAME_NOT_FOUND)

        val playerId = UUID.randomUUID().toString()

        room.mutex.withLock {
            val updatedState = room.session.addPlayer(playerId, playerName)
            persistSafely(room.session)
            return RoomActionResult(gameId, playerId, updatedState)
        }
    }

    /**
     * Removes a player from a game
     */
    suspend fun leaveGame(
        gameId: String,
        playerId: String,
    ): GameState {
        val room = fetchGameRoom(gameId)

        room.mutex.withLock {
            val updatedState = room.session.removePlayer(playerId)
            if (updatedState.players.isEmpty()) rooms.remove(gameId)
            persistSafely(room.session)
            return updatedState
        }
    }

    suspend fun getStateForReconnection(
        gameId: String,
        playerId: String,
    ): GameState {
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

    suspend fun chooseAuction(
        gameId: String,
        actorId: String,
    ): GameState {
        val room = fetchGameRoom(gameId)

        room.mutex.withLock {
            val state = room.session.chooseAuction(actorId)
            persistSafely(room.session)
            scheduleAuctionAutoClose(gameId)
            return state
        }
    }

    suspend fun placeBid(
        gameId: String,
        actorId: String,
        amount: Int,
    ): GameState {
        val room = fetchGameRoom(gameId)

        room.mutex.withLock {
            val state = room.session.placeBid(actorId, amount)
            persistSafely(room.session)
            scheduleAuctionAutoClose(gameId)
            return state
        }
    }

    private fun scheduleAuctionAutoClose(gameId: String) {
        val room = rooms[gameId] ?: return
        val endTime =
            room.session.state.auctionState
                ?.timerEndTime ?: return

        serviceScope.launch {
            delay(5100) // Wait slightly longer than the timer to be safe
            val currentRoom = rooms[gameId] ?: return@launch

            currentRoom.mutex.withLock {
                // If the timerEndTime is still the same, it means no new bid happened
                if (currentRoom.session.state.auctionState
                        ?.timerEndTime == endTime
                ) {
                    val updatedState = currentRoom.session.closeAuctionAfterTimeout()
                    persistSafely(currentRoom.session)
                    eventPublisher.publishEvent(GameStateChangedEvent(gameId, updatedState))
                }
            }
        }
    }

    suspend fun resolveAuction(
        gameId: String,
        actorId: String,
        auctioneerBuysCard: Boolean,
    ): GameState {
        val room = fetchGameRoom(gameId)

        room.mutex.withLock {
            val state = room.session.resolveAuction(actorId, auctioneerBuysCard)
            persistSafely(room.session)
            return state
        }
    }

    suspend fun chooseTrade(
        gameId: String,
        actorId: String,
        targetId: String,
        animalType: AnimalType,
        offeredMoneyCardIds: Set<String>,
    ): GameState {
        val room = fetchGameRoom(gameId)

        room.mutex.withLock {
            val state =
                room.session.chooseTrade(
                    actorId = actorId,
                    targetId = targetId,
                    animalType = animalType,
                    offeredMoneyCardIds = offeredMoneyCardIds,
                )
            persistSafely(room.session)
            return state
        }
    }

    suspend fun respondToTrade(
        gameId: String,
        actorId: String,
        counterOfferedMoneyCardIds: Set<String>,
    ): GameState {
        val room = fetchGameRoom(gameId)

        room.mutex.withLock {
            val state =
                room.session.respondToTrade(
                    actorId = actorId,
                    counterOfferedMoneyCardIds = counterOfferedMoneyCardIds,
                )
            persistSafely(room.session)
            return state
        }
    }

    suspend fun finishTradeReveal(
        gameId: String,
        actorId: String,
    ): GameState {
        val room = fetchGameRoom(gameId)

        room.mutex.withLock {
            val state = room.session.endTradeReveal()
            persistSafely(room.session)
            return state
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
     * Generates a unique 5-digit game code.
     */
    private fun generateGameCode(): String {
        var code: String

        do {
            code = Random.nextInt(10000, 100000).toString()
        } while (rooms.containsKey(code))

        return code
    }

    private fun fetchGameRoom(gameId: String): SyncGameRoom =
        checkNotNull(rooms[gameId]) {
            "Game registry does not contain game session $gameId"
        }
}

private class SyncGameRoom(
    val session: GameSession,
    val mutex: Mutex = Mutex(),
)
