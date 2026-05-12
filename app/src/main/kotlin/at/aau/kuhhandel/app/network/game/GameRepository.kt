package at.aau.kuhhandel.app.network.game

import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.websocket.ErrorPayload
import at.aau.kuhhandel.shared.websocket.GameCreatedPayload
import at.aau.kuhhandel.shared.websocket.GameStatePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameRepositoryState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val gameId: String? = null,
    val myPlayerId: String? = null,
    val gameState: GameState? = null,
    val errorMessage: String? = null,
)

/**
 * Repository responsible for managing the WebSocket connection and
 * maintaining the raw game state received from the server.
 */
class GameRepository(
    private val client: GameWebSocketClient,
    private val scope: CoroutineScope,
) {
    private companion object {
        const val CONNECTION_FAILED = "Connection failed"
        const val CONNECTION_LOST = "Connection lost"
    }

    private val _state = MutableStateFlow(GameRepositoryState())
    val state: StateFlow<GameRepositoryState> = _state.asStateFlow()

    private var eventsJob: Job? = null

    suspend fun createGame(playerName: String? = null) {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.createGame(playerName)
    }

    /*
    suspend fun joinGame(
        gameId: String,
        playerName: String? = null,
    ) {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.joinGame(gameId, playerName)
    }
     */

    suspend fun startGame() {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.startGame()
    }

    suspend fun revealCard() {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.revealCard()
    }

    suspend fun placeBid(amount: Int) {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.placeBid(amount)
    }

    suspend fun buyBack(buyBack: Boolean) {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.buyBack(buyBack)
    }

    suspend fun initiateTrade(targetPlayerId: String) {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.initiateTrade(targetPlayerId)
    }

    suspend fun offerTrade(moneyCardIds: List<String>) {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.offerTrade(moneyCardIds)
    }

    suspend fun respondToTrade(accepted: Boolean) {
        ensureConnected()
        _state.update { it.copy(errorMessage = null) }
        client.respondToTrade(accepted)
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun disconnect() {
        scope.launch {
            val activeJob = eventsJob
            eventsJob = null
            activeJob?.cancel()
            client.disconnect()
            _state.value = GameRepositoryState()
        }
    }

    private suspend fun ensureConnected() {
        if (awaitExistingConnection()) {
            return
        }

        startConnecting()
        val events = connectEvents()
        val collectorJob = launchCollector(events)
        awaitInitialConnection(collectorJob)
    }

    private suspend fun awaitExistingConnection(): Boolean {
        if (eventsJob == null) {
            return false
        }

        client.awaitConnected()
        return true
    }

    private fun startConnecting() {
        _state.update { it.copy(isConnecting = true, errorMessage = null) }
    }

    private fun connectEvents(): Flow<WebSocketEnvelope> =
        try {
            client.connect()
        } catch (e: Exception) {
            reportConnectionFailure(e)
            throw e
        }

    private fun launchCollector(events: Flow<WebSocketEnvelope>): Job =
        scope
            .launch(start = CoroutineStart.LAZY) {
                collectEvents(events)
            }.also { collectorJob ->
                eventsJob = collectorJob
                collectorJob.start()
            }

    private suspend fun collectEvents(events: Flow<WebSocketEnvelope>) {
        val collectorJob = currentCoroutineContext()[Job]
        try {
            events.collect(::handleEnvelope)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportCollectorFailure(e, collectorJob)
        } finally {
            finishCollector(collectorJob)
        }
    }

    private fun reportCollectorFailure(
        throwable: Throwable,
        collectorJob: Job?,
    ) {
        if (eventsJob !== collectorJob) {
            return
        }

        _state.update {
            it.copy(
                errorMessage = formatThrowable(connectionErrorPrefix(), throwable),
            )
        }
    }

    private fun finishCollector(collectorJob: Job?) {
        if (eventsJob !== collectorJob) {
            return
        }

        eventsJob = null
        _state.update { it.copy(isConnecting = false, isConnected = false) }
    }

    private suspend fun awaitInitialConnection(collectorJob: Job) {
        try {
            client.awaitConnected()
            _state.update { it.copy(isConnecting = false, isConnected = true, errorMessage = null) }
        } catch (e: Exception) {
            cancelCollector(collectorJob)
            reportConnectionFailure(e)
            throw e
        }
    }

    private fun cancelCollector(collectorJob: Job) {
        if (eventsJob === collectorJob) {
            eventsJob = null
        }
        collectorJob.cancel()
    }

    private fun reportConnectionFailure(throwable: Throwable) {
        _state.update {
            it.copy(
                isConnecting = false,
                isConnected = false,
                errorMessage = formatThrowable(CONNECTION_FAILED, throwable),
            )
        }
    }

    private fun connectionErrorPrefix(): String =
        if (_state.value.isConnected) {
            CONNECTION_LOST
        } else {
            CONNECTION_FAILED
        }

    private fun handleEnvelope(envelope: WebSocketEnvelope) {
        when (envelope.type) {
            WebSocketType.GAME_CREATED -> {
                val payload =
                    decodePayload(
                        envelope = envelope,
                        serializer = GameCreatedPayload.serializer(),
                        invalidMessage = "Invalid GAME_CREATED message",
                    ) ?: return

                _state.update {
                    it.copy(
                        gameId = payload.gameId,
                        myPlayerId = payload.playerId,
                        gameState = payload.state,
                        errorMessage = null,
                    )
                }
            }

            WebSocketType.GAME_STARTED,
            WebSocketType.GAME_STATE_UPDATED,
            -> {
                val payload =
                    decodePayload(
                        envelope = envelope,
                        serializer = GameStatePayload.serializer(),
                        invalidMessage = "Invalid GameState message",
                    ) ?: return

                _state.update {
                    it.copy(
                        gameState = payload.state,
                        errorMessage = null,
                    )
                }
            }

            WebSocketType.ERROR -> {
                val payload =
                    decodePayload(
                        envelope = envelope,
                        serializer = ErrorPayload.serializer(),
                        invalidMessage = "Invalid ERROR message",
                    ) ?: return

                _state.update { it.copy(errorMessage = payload.message) }
            }

            else -> Unit
        }
    }

    private fun <T> decodePayload(
        envelope: WebSocketEnvelope,
        serializer: kotlinx.serialization.KSerializer<T>,
        invalidMessage: String,
    ): T? =
        runCatching {
            WebSocketJson.json.decodeFromJsonElement(
                serializer,
                requireNotNull(envelope.payload),
            )
        }.getOrElse {
            _state.update { it.copy(errorMessage = invalidMessage) }
            null
        }

    private fun formatThrowable(
        prefix: String,
        throwable: Throwable,
    ): String {
        val type = throwable::class.simpleName ?: "Error"
        val message = throwable.message?.takeIf { it.isNotBlank() }
        return if (message != null) {
            "$prefix: $type - $message"
        } else {
            "$prefix: $type"
        }
    }
}
