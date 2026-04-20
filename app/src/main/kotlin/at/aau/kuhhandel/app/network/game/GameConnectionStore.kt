package at.aau.kuhhandel.app.network.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class GameConnectionUiState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val gameId: String? = null,
    val gameState: GameState? = null,
    val errorMessage: String? = null,
)

class GameConnectionStore(
    private val client: GameWebSocketClient,
    private val scope: CoroutineScope,
) {
    private companion object {
        const val CONNECTION_FAILED = "Verbindung fehlgeschlagen"
        const val CONNECTION_LOST = "Verbindung verloren"
    }

    var uiState by mutableStateOf(GameConnectionUiState())
        private set

    private var eventsJob: Job? = null

    suspend fun createGame(playerName: String? = null) {
        ensureConnected()
        uiState = uiState.copy(errorMessage = null)
        client.createGame(playerName)
    }

    suspend fun startGame() {
        ensureConnected()
        uiState = uiState.copy(errorMessage = null)
        client.startGame()
    }

    suspend fun revealCard() {
        ensureConnected()
        uiState = uiState.copy(errorMessage = null)
        client.revealCard()
    }

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    fun disconnect() {
        scope.launch {
            val activeJob = eventsJob
            eventsJob = null
            activeJob?.cancel()
            client.disconnect()
            uiState = GameConnectionUiState()
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
        uiState = uiState.copy(isConnecting = true, errorMessage = null)
    }

    private suspend fun connectEvents(): Flow<WebSocketEnvelope> =
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

        uiState =
            uiState.copy(
                errorMessage = formatThrowable(connectionErrorPrefix(), throwable),
            )
    }

    private fun finishCollector(collectorJob: Job?) {
        if (eventsJob !== collectorJob) {
            return
        }

        eventsJob = null
        uiState = uiState.copy(isConnecting = false, isConnected = false)
    }

    private suspend fun awaitInitialConnection(collectorJob: Job) {
        try {
            client.awaitConnected()
            uiState = uiState.copy(isConnecting = false, isConnected = true, errorMessage = null)
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
        uiState =
            uiState.copy(
                isConnecting = false,
                isConnected = false,
                errorMessage = formatThrowable(CONNECTION_FAILED, throwable),
            )
    }

    private fun connectionErrorPrefix(): String =
        if (uiState.isConnected) {
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
                        invalidMessage = "Ungueltige GAME_CREATED-Nachricht",
                    ) ?: return

                uiState =
                    uiState.copy(
                        gameId = payload.gameId,
                        gameState = payload.state,
                        errorMessage = null,
                    )
            }

            WebSocketType.GAME_STARTED,
            WebSocketType.GAME_STATE_UPDATED,
            -> {
                val payload =
                    decodePayload(
                        envelope = envelope,
                        serializer = GameStatePayload.serializer(),
                        invalidMessage = "Ungueltige GameState-Nachricht",
                    ) ?: return

                uiState =
                    uiState.copy(
                        gameState = payload.state,
                        errorMessage = null,
                    )
            }

            WebSocketType.ERROR -> {
                val payload =
                    decodePayload(
                        envelope = envelope,
                        serializer = ErrorPayload.serializer(),
                        invalidMessage = "Ungueltige ERROR-Nachricht",
                    ) ?: return

                uiState = uiState.copy(errorMessage = payload.message)
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
            uiState = uiState.copy(errorMessage = invalidMessage)
            null
        }

    private fun formatThrowable(
        prefix: String,
        throwable: Throwable,
    ): String {
        val type = throwable::class.simpleName ?: "Fehler"
        val message = throwable.message?.takeIf { it.isNotBlank() }
        return if (message != null) {
            "$prefix: $type - $message"
        } else {
            "$prefix: $type"
        }
    }
}
