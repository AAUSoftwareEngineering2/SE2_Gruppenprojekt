package at.aau.kuhhandel.server.service

import at.aau.kuhhandel.server.cluster.ClusterUpdateNotifier
import at.aau.kuhhandel.server.event.GameStateChangedEvent
import at.aau.kuhhandel.server.exception.GameException
import at.aau.kuhhandel.server.model.GameSession
import at.aau.kuhhandel.server.model.RoomActionResult
import at.aau.kuhhandel.server.persistence.GamePersistenceService
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GameErrorReason
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.PlayerNameRules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.random.Random

/**
 * Core domain orchestration layer for the server.
 *
 * The database holds the authoritative game state. Every mutation goes through
 * [GamePersistenceService.mutateGameState] (row lock, load, apply [GameSession], save), so the
 * service keeps no game state in memory and multiple pods can work on the same games.
 *
 * Time-based transitions (phase timeouts and spy-reveal expiry) run from the periodic sweeps
 * [sweepExpiredTimeouts] and [sweepExpiredSpies], driven by
 * [at.aau.kuhhandel.server.cluster.TimeoutSweeper]. In-memory timer coroutines would die with
 * their pod and only fire on one pod, so the deadlines live in the database instead.
 */
@Service
class GameService(
    private val eventPublisher: ApplicationEventPublisher,
    private val persistenceService: GamePersistenceService,
    private val leaderboardService: LeaderboardService,
    private val gameSessionFactory: (String, String, String) -> GameSession = ::GameSession,
    private val clusterNotifier: ClusterUpdateNotifier? = null,
    private val gameCodeGenerator: () -> String = {
        Random.nextInt(GAME_CODE_MIN, GAME_CODE_BOUND).toString()
    },
    // Injectable so tests can pin it; defaults to IO for the blocking JDBC work off the caller.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Creates a new game with a unique 5-digit game id and persists the lobby snapshot.
     * A code collision with another pod fails on the primary key and is retried.
     */
    fun createGame(rawHostPlayerName: String): RoomActionResult {
        val playerId = generatePlayerId()
        val playerName = resolvePlayerName(rawHostPlayerName)

        repeat(CREATE_GAME_MAX_ATTEMPTS) {
            val gameId = generateGameCode()
            val session = gameSessionFactory(gameId, playerId, playerName)

            try {
                persistenceService.saveGameState(gameId, session.state)
                return RoomActionResult(gameId, playerId, session.state)
            } catch (e: DataIntegrityViolationException) {
                logger.info("Game code $gameId collided on insert, retrying", e)
            }
        }
        error("Could not allocate a unique game code after $CREATE_GAME_MAX_ATTEMPTS attempts")
    }

    /**
     * Returns the game as a [GameSession] around the persisted state, or null when it does
     * not exist.
     */
    fun getGame(gameId: String): GameSession? {
        val state = persistenceService.loadGameState(gameId) ?: return null
        return GameSession.fromState(gameId, state)
    }

    /**
     * Removes both the persisted snapshot and everything attached to [gameId].
     */
    fun purgeGame(gameId: String) {
        runCatching { persistenceService.deleteGame(gameId) }
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
        val playerId = generatePlayerId()
        val playerName = resolvePlayerName(rawPlayerName)
        val newState =
            executeAction(gameId) { session -> session.addPlayer(playerId, playerName) }
        return RoomActionResult(gameId, playerId, newState)
    }

    /**
     * Removes a player from a game. Deletes the game entirely once the last player left.
     */
    suspend fun leaveGame(
        gameId: String,
        playerId: String,
    ): GameState {
        val newState = executeAction(gameId) { session -> session.removePlayer(playerId) }
        if (newState.hasNoPlayers()) {
            withContext(ioDispatcher) { purgeGame(gameId) }
        }
        return newState
    }

    /**
     * Disconnects a player from a game and deletes the game if it has no players.
     *
     * Expects a valid [gameId].
     */
    suspend fun disconnectPlayer(
        gameId: String,
        playerId: String,
    ): GameState {
        val newState = executeAction(gameId) { session -> session.disconnectPlayer(playerId) }
        if (!newState.hasPlayer(playerId)) {
            withContext(ioDispatcher) { purgeGame(gameId) }
        }

        return newState
    }

    /**
     * Reconnects a player to a game.
     *
     * Expects a valid [gameId].
     */
    suspend fun reconnectPlayer(
        gameId: String,
        playerId: String,
    ): GameState = executeAction(gameId) { session -> session.reconnectPlayer(playerId) }

    /**
     * Persists the reconnect token (hashed) for later validation.
     */
    suspend fun storeReconnectToken(
        gameId: String,
        playerId: String,
        token: String,
    ) {
        val stored =
            withContext(ioDispatcher) {
                persistenceService.storeReconnectToken(gameId, playerId, token)
            }
        if (!stored) {
            logger.warn("Could not store reconnect token for player $playerId in game $gameId")
        }
    }

    /**
     * Hash of the player's current reconnect token. Changes whenever the player reconnects.
     */
    suspend fun reconnectTokenFingerprint(
        gameId: String,
        playerId: String,
    ): String? =
        withContext(ioDispatcher) {
            persistenceService.reconnectTokenFingerprint(gameId, playerId)
        }

    /**
     * Validates a reconnect token against the database.
     */
    suspend fun isReconnectTokenValid(
        gameId: String,
        playerId: String,
        token: String,
    ): Boolean =
        withContext(ioDispatcher) {
            persistenceService.isReconnectTokenValid(gameId, playerId, token)
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
     * Submits the auction buyer's selected money cards as payment.
     *
     * Expects a valid [gameId].
     */
    suspend fun submitAuctionPayment(
        gameId: String,
        actorId: String,
        moneyCardIds: Set<String>,
    ): GameState =
        executeAction(gameId) { session ->
            session.submitAuctionPayment(
                actorId,
                moneyCardIds,
            )
        }

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
    ): GameState = executeAction(gameId) { session -> session.spy(actorId, targetId) }

    /**
     * Catches all players currently spying on the actor.
     *
     * Expects a valid [gameId].
     */
    suspend fun catchSpy(
        gameId: String,
        actorId: String,
    ): GameState = executeAction(gameId) { session -> session.catchSpy(actorId) }

    /**
     * Advances every game whose phase timer expired and returns their ids. Safe to run on all
     * pods at once: the advance re-checks the deadline under the row lock, so only the first
     * pod actually advances a game.
     */
    fun sweepExpiredTimeouts(now: Long = System.currentTimeMillis()): List<String> {
        val dueGameIds = persistenceService.findGameIdsWithExpiredTimers(now)
        val advancedGames = mutableListOf<String>()

        dueGameIds.forEach { gameId ->
            runCatching {
                var advancedState: GameState? = null
                persistenceService.mutateGameState(gameId, activityAt = null) { current ->
                    val timerEnd = current.timerEnd
                    if (timerEnd != null && timerEnd <= now) {
                        GameSession
                            .fromState(gameId, current)
                            .handleTimeoutExpiration()
                            .also { advancedState = it }
                    } else {
                        // Another pod advanced this game between query and lock, leave as is.
                        current
                    }
                }
                advancedState?.let { newState ->
                    advancedGames += gameId
                    checkAndStoreLeaderboard(newState)
                    eventPublisher.publishEvent(GameStateChangedEvent(gameId, newState))
                    clusterNotifier?.gameUpdated(gameId)
                }
            }.onFailure { logger.warn("Timeout sweep failed for game $gameId", it) }
        }

        return advancedGames
    }

    /**
     * Clears spy reveals whose window expired and returns the affected game ids. The stateless
     * replacement for the old in-memory spy timer: the deadline lives in the database, so any pod
     * can clear it and notify the players. Re-checks under the row lock, so a concurrent clear by
     * another pod is a harmless no-op.
     */
    fun sweepExpiredSpies(now: Long = System.currentTimeMillis()): List<String> {
        val dueGameIds = persistenceService.findGameIdsWithExpiredSpies(now)
        val clearedGames = mutableListOf<String>()

        dueGameIds.forEach { gameId ->
            runCatching {
                var clearedState: GameState? = null
                persistenceService.mutateGameState(gameId, activityAt = null) { current ->
                    val next = GameSession.fromState(gameId, current).clearExpiredSpies()
                    if (next.activeSpies != current.activeSpies) {
                        next.also { clearedState = it }
                    } else {
                        // Another pod already cleared the expired spies between query and lock.
                        current
                    }
                }
                clearedState?.let { newState ->
                    clearedGames += gameId
                    checkAndStoreLeaderboard(newState)
                    eventPublisher.publishEvent(GameStateChangedEvent(gameId, newState))
                    clusterNotifier?.gameUpdated(gameId)
                }
            }.onFailure { logger.warn("Spy expiration sweep failed for game $gameId", it) }
        }

        return clearedGames
    }

    /**
     * Purges games that have seen no real player activity since [cutoff] (timeout advances do not
     * count as activity, see [GameSession.handleTimeoutExpiration] / the sweeper). Safe on every
     * pod: the delete is idempotent, so a pod that loses the race simply finds nothing to delete.
     */
    fun reapStaleGames(cutoff: Long): List<String> {
        val reaped = mutableListOf<String>()
        persistenceService.findStaleGameIds(cutoff).forEach { gameId ->
            runCatching { persistenceService.deleteGame(gameId) }
                .onSuccess { reaped += gameId }
                .onFailure { logger.warn("Failed to reap stale game $gameId", it) }
        }
        if (reaped.isNotEmpty()) {
            logger.info("Reaped {} stale games: {}", reaped.size, reaped)
        }
        return reaped
    }

    /**
     * Centralized helper for all game mutations: runs the action inside a row-locked
     * transaction and notifies the peer pods afterwards.
     */
    private suspend fun executeAction(
        gameId: String,
        action: (GameSession) -> GameState,
    ): GameState {
        val newState =
            withContext(ioDispatcher) {
                persistenceService.mutateGameState(gameId) { current ->
                    action(GameSession.fromState(gameId, current))
                }
            } ?: throw GameException(GameErrorReason.GAME_NOT_FOUND)

        checkAndStoreLeaderboard(newState)

        clusterNotifier?.gameUpdated(gameId)
        return newState
    }

    /**
     * Stores final player rankings in the leaderboard if a game is finished.
     */
    private fun checkAndStoreLeaderboard(newState: GameState) {
        if (newState.isFinished()) {
            val rankings = newState.finalRanking
            checkNotNull(rankings) { "Final ranking is null in a finished game" }
            leaderboardService.storeScores(rankings)
        }
    }

    /**
     * Validates and normalizes a raw player name, throwing [GameErrorReason.INVALID_PLAYER_NAME]
     * when it does not satisfy [PlayerNameRules].
     */
    private fun resolvePlayerName(rawName: String): String {
        val trimmed = rawName.trim()
        if (!PlayerNameRules.isValid(trimmed)) {
            throw GameException(GameErrorReason.INVALID_PLAYER_NAME)
        }
        return trimmed
    }

    /**
     * Generates a 5-digit game code that is not taken in the database.
     */
    private fun generateGameCode(): String {
        var code: String

        do {
            code = gameCodeGenerator()
        } while (persistenceService.existsGame(code))

        return code
    }

    /**
     * Generates a unique player identifier.
     */
    private fun generatePlayerId(): String = UUID.randomUUID().toString()

    companion object {
        private const val GAME_CODE_MIN = 10000
        private const val GAME_CODE_BOUND = 100000
        private const val CREATE_GAME_MAX_ATTEMPTS = 5
    }
}
