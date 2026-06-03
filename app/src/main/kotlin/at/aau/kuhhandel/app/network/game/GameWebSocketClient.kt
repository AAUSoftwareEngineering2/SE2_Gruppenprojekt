package at.aau.kuhhandel.app.network.game

import at.aau.kuhhandel.app.network.ApiConfig
import at.aau.kuhhandel.app.network.NetworkClientFactory
import at.aau.kuhhandel.shared.websocket.AuctionBuyBackPayload
import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.InitiateTradePayload
import at.aau.kuhhandel.shared.websocket.JoinGamePayload
import at.aau.kuhhandel.shared.websocket.PlaceBidPayload
import at.aau.kuhhandel.shared.websocket.ReconnectPayload
import at.aau.kuhhandel.shared.websocket.RespondToTradePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketRoutes
import at.aau.kuhhandel.shared.websocket.WebSocketType
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.util.UUID

class OpenedSession(
    val session: WebSocketSession,
    private val extraCleanup: suspend () -> Unit = {},
) {
    suspend fun close() {
        runCatching { session.close() }
        runCatching { extraCleanup() }
    }
}

/** Talks to the server's GameWebSocketHandler — one connection, typed commands, events as Flow. */
class GameWebSocketClient(
    private val clientFactory: () -> HttpClient = NetworkClientFactory::create,
    private val webSocketUrl: String = "${ApiConfig.WS_URL}${WebSocketRoutes.GAME}",
    openSession: (suspend () -> OpenedSession)? = null,
) {
    private var current: OpenedSession? = null
    private var pendingConnection: CompletableDeferred<Unit>? = null
    private var connectionFailure: Throwable? = null
    private var isIntentionalDisconnect = false
    private val openSession: suspend () -> OpenedSession = openSession ?: ::defaultOpenSession

    /** Returns the connection Flow. The WebSocket opens when collection starts. */
    fun connect(): Flow<WebSocketEnvelope> {
        ensureDisconnected()
        val ready = preparePendingConnection()
        return flow {
            val opened = openSessionOrThrow(ready)
            markConnected(opened, ready)
            try {
                emitIncomingEnvelopes(opened)
            } finally {
                cleanupConnection(opened, ready)
            }
        }
    }

    private fun ensureDisconnected() {
        check(current == null && pendingConnection == null) {
            "Already connected. Call disconnect() first."
        }
    }

    private fun preparePendingConnection(): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { ready ->
            connectionFailure = null
            pendingConnection = ready
            isIntentionalDisconnect = false
        }

    /** Initiates the raw Ktor WebSocket session and handles connection lifecycle errors. */
    private suspend fun openSessionOrThrow(ready: CompletableDeferred<Unit>): OpenedSession =
        try {
            openSession()
        } catch (e: Exception) {
            connectionFailure = e
            clearPendingConnection(ready)
            ready.completeExceptionally(e)
            throw e
        }

    private fun markConnected(
        opened: OpenedSession,
        ready: CompletableDeferred<Unit>,
    ) {
        current = opened
        connectionFailure = null
        ready.complete(Unit)
    }

    /** Continuously reads from the session's incoming channel and emits decoded envelopes. */
    private suspend fun FlowCollector<WebSocketEnvelope>.emitIncomingEnvelopes(
        opened: OpenedSession,
    ) {
        val closeDetails = consumeIncomingFrames(opened)
        if (current === opened && !isIntentionalDisconnect) {
            error(closeDetails ?: "WebSocket connection lost unexpectedly")
        }
    }

    /** Iterates over incoming frames, handling close messages and extracting text data. */
    private suspend fun FlowCollector<WebSocketEnvelope>.consumeIncomingFrames(
        opened: OpenedSession,
    ): String? {
        for (frame in opened.session.incoming) {
            closeDetails(frame)?.let { return it }
            decodeEnvelope(frame)?.let { emit(it) }
        }
        return null
    }

    /** Extracts the close reason from a WebSocket close frame. */
    private fun closeDetails(frame: Frame): String? =
        (frame as? Frame.Close)?.let { closeFrame ->
            formatCloseDetails(closeFrame.readReason())
        }

    private fun formatCloseDetails(reason: CloseReason?): String =
        if (reason != null) {
            "WebSocket closed (${reason.code}): ${
                reason.message.ifBlank { "No reason given" }
            }"
        } else {
            "WebSocket closed without a close reason"
        }

    /** Parses a raw text frame into a [WebSocketEnvelope]. */
    private fun decodeEnvelope(frame: Frame): WebSocketEnvelope? {
        val textFrame = frame as? Frame.Text ?: return null
        return runCatching {
            WebSocketJson.json.decodeFromString(
                WebSocketEnvelope.serializer(),
                textFrame.readText(),
            )
        }.getOrNull()
    }

    /** Closes the session and cleans up resources associated with a specific connection. */
    private suspend fun cleanupConnection(
        opened: OpenedSession,
        ready: CompletableDeferred<Unit>,
    ) {
        if (current === opened) {
            current = null
            opened.close()
        }
        clearPendingConnection(ready)
    }

    private fun clearPendingConnection(ready: CompletableDeferred<Unit>) {
        if (pendingConnection === ready) {
            pendingConnection = null
        }
    }

    suspend fun awaitConnected() {
        pendingConnection?.await()
            ?: connectionFailure?.let { throw it }
            ?: check(current != null) { "Not connected. Call connect() first." }
    }

    /** Sends CREATE_GAME. Returns the requestId so the caller can match the server reply well. */
    suspend fun createGame(playerName: String? = null): String {
        val requestId = UUID.randomUUID().toString()
        val payload =
            WebSocketJson.json.encodeToJsonElement(
                CreateGamePayload.serializer(),
                CreateGamePayload(playerName = playerName),
            )
        send(WebSocketEnvelope(WebSocketType.CREATE_GAME, requestId, payload))
        return requestId
    }

    /** Joins an existing game lobby. */
    suspend fun joinGame(
        gameId: String,
        playerName: String? = null,
    ): String {
        val requestId = UUID.randomUUID().toString()
        val payload =
            WebSocketJson.json.encodeToJsonElement(
                JoinGamePayload.serializer(),
                JoinGamePayload(gameId = gameId, playerName = playerName),
            )
        send(WebSocketEnvelope(WebSocketType.JOIN_GAME, requestId, payload))
        return requestId
    }

    /** Re-establishes a connection to a game in progress. */
    suspend fun reconnect(
        gameId: String,
        playerId: String,
        token: String,
    ): String {
        val requestId = UUID.randomUUID().toString()
        val payload =
            WebSocketJson.json.encodeToJsonElement(
                ReconnectPayload.serializer(),
                ReconnectPayload(gameId = gameId, playerId = playerId, token = token),
            )
        send(WebSocketEnvelope(WebSocketType.RECONNECT, requestId, payload))
        return requestId
    }

    /** Leaves the current game or lobby. */
    suspend fun leaveGame(): String {
        val requestId = UUID.randomUUID().toString()
        send(WebSocketEnvelope(WebSocketType.LEAVE_GAME, requestId))
        return requestId
    }

    /** Signals to start the game when all players are ready. */
    suspend fun startGame(): String {
        val requestId = UUID.randomUUID().toString()
        send(WebSocketEnvelope(WebSocketType.START_GAME, requestId))
        return requestId
    }

    /** Reveals the next animal card for auction. */
    suspend fun revealCard(): String {
        val requestId = UUID.randomUUID().toString()
        send(WebSocketEnvelope(WebSocketType.CHOOSE_AUCTION, requestId))
        return requestId
    }

    /** Places a bid on the current auction. */
    suspend fun placeBid(amount: Int): String {
        val requestId = UUID.randomUUID().toString()
        val payload =
            WebSocketJson.json.encodeToJsonElement(
                PlaceBidPayload.serializer(),
                PlaceBidPayload(amount = amount),
            )
        send(WebSocketEnvelope(WebSocketType.PLACE_BID, requestId, payload))
        return requestId
    }

    /** Decides whether the auctioneer buys back the animal. */
    suspend fun buyBack(buyBack: Boolean): String {
        val requestId = UUID.randomUUID().toString()
        val payload =
            WebSocketJson.json.encodeToJsonElement(
                AuctionBuyBackPayload.serializer(),
                AuctionBuyBackPayload(buyBack = buyBack),
            )
        send(WebSocketEnvelope(WebSocketType.AUCTION_BUY_BACK, requestId, payload))
        return requestId
    }

    /** Starts a "Kuhhandel" trade with another player. */
    suspend fun initiateTrade(
        challengedPlayerId: String,
        animalType: at.aau.kuhhandel.shared.enums.AnimalType,
        moneyCardIds: Set<String>,
    ): String {
        val requestId = UUID.randomUUID().toString()
        val payload =
            WebSocketJson.json.encodeToJsonElement(
                InitiateTradePayload.serializer(),
                InitiateTradePayload(
                    challengedPlayerId = challengedPlayerId,
                    animalType = animalType,
                    moneyCardIds = moneyCardIds,
                ),
            )
        send(WebSocketEnvelope(WebSocketType.INITIATE_TRADE, requestId, payload))
        return requestId
    }

    /** Counters a trade offer with own money cards. */
    suspend fun respondToTrade(
        respondingPlayerId: String,
        counterOfferedMoneyCardIds: Set<String> = emptySet(),
    ): String {
        val requestId = UUID.randomUUID().toString()
        val payload =
            WebSocketJson.json.encodeToJsonElement(
                RespondToTradePayload.serializer(),
                RespondToTradePayload(
                    respondingPlayerId = respondingPlayerId,
                    counterOfferedMoneyCardIds = counterOfferedMoneyCardIds,
                ),
            )
        send(WebSocketEnvelope(WebSocketType.RESPOND_TO_TRADE, requestId, payload))
        return requestId
    }

    /** Completes the visual reveal phase of a trade. */
    suspend fun finishTradeReveal(): String {
        val requestId = UUID.randomUUID().toString()
        send(WebSocketEnvelope(WebSocketType.FINISH_TRADE_REVEAL, requestId))
        return requestId
    }

    /** Closes the current WebSocket connection and stops any active sessions. */
    suspend fun disconnect() {
        isIntentionalDisconnect = true
        pendingConnection = null
        connectionFailure = null
        val active = current ?: return
        current = null
        active.close()
    }

    /** Serializes and sends an envelope over the active session. */
    private suspend fun send(envelope: WebSocketEnvelope) {
        val active = current?.session ?: error("Not connected. Call connect() first.")
        val text = WebSocketJson.json.encodeToString(WebSocketEnvelope.serializer(), envelope)
        active.send(Frame.Text(text))
    }

    /** Opens a new WebSocket session using the configured client factory and URL. */
    private suspend fun defaultOpenSession(): OpenedSession {
        val client = clientFactory()
        return try {
            val session =
                client.webSocketSession {
                    url(webSocketUrl)
                }
            OpenedSession(session) { client.close() }
        } catch (e: Exception) {
            client.close()
            throw e
        }
    }
}
