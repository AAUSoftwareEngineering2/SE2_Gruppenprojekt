package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.exception.GameException
import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.server.model.RoomActionResult
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GameErrorReason
import at.aau.kuhhandel.shared.model.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Service
class GameService(
    private val eventPublisher: ApplicationEventPublisher,
    private val gameSessionFactory: (String, String, String) -> GameSession = ::GameSession,
    // Used in tests
    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {

    // Stores all active game sessions by their 5-digit game id
    private val rooms: ConcurrentHashMap<String, SyncGameRoom> = ConcurrentHashMap()

    /**
     * Creates a new game with a unique 5-digit game id.
     */
    suspend fun createGame(hostPlayerName: String): RoomActionResult {
        val gameId: String
        val playerId = UUID.randomUUID().toString()
        val session: GameSession

        synchronized(rooms) {
            gameId = generateGameCode()
            session = gameSessionFactory(gameId, playerId, hostPlayerName)

            rooms[gameId] = SyncGameRoom(session)
        }

        return RoomActionResult(gameId, playerId, session.state)
    }

    /**
     * Returns a game session by its game id.
     */
    fun getGame(gameId: String): GameSession? = rooms[gameId]?.session

    /**
     * Removes the game session with the given game id.
     */
    fun removeGame(gameId: String) {
        rooms.remove(gameId)
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
            return room.session.startGame(actorId)
        }
    }

    /**
     * Adds a player to a game
     */
    suspend fun joinGame(
        gameId: String,
        playerName: String,
    ): RoomActionResult {
        val room = fetchGameRoom(gameId)
        val playerId = UUID.randomUUID().toString()

        room.mutex.withLock {
            val updatedState = room.session.addPlayer(playerId, playerName)
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
            return room.session.resolveAuction(actorId, auctioneerBuysCard)
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
            return room.session.chooseTrade(
                actorId = actorId,
                targetId = targetId,
                animalType = animalType,
                offeredMoneyCardIds = offeredMoneyCardIds,
            )
        }
    }

    suspend fun respondToTrade(
        gameId: String,
        actorId: String,
        counterOfferedMoneyCardIds: Set<String>,
    ): GameState {
        val room = fetchGameRoom(gameId)

        room.mutex.withLock {
            return room.session.respondToTrade(
                actorId = actorId,
                counterOfferedMoneyCardIds = counterOfferedMoneyCardIds,
            )
        }
    }

    suspend fun finishTradeReveal(
        gameId: String,
        actorId: String,
    ): GameState {
        val room = fetchGameRoom(gameId)

        room.mutex.withLock {
            return room.session.endTradeReveal()
        }
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
