package at.aau.kuhhandel.app.network.game

import at.aau.kuhhandel.app.network.ApiConfig
import at.aau.kuhhandel.app.network.NetworkClientFactory
import at.aau.kuhhandel.shared.websocket.ChooseTradePayload
import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.JoinGamePayload
import at.aau.kuhhandel.shared.websocket.PlaceBidPayload
import at.aau.kuhhandel.shared.websocket.ReconnectPayload
import at.aau.kuhhandel.shared.websocket.ResolveAuctionPayload
import at.aau.kuhhandel.shared.websocket.RespondToTradePayload
import at.aau.kuhhandel.shared.websocket.SpyPayload
import at.aau.kuhhandel.shared.websocket.SubmitAuctionPaymentPayload
import at.aau.kuhhandel.shared.websocket.SubmitTradeMoneyPayload
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

// dein Wrapper um die Ktor-Session + eine Aufräum-Funktion (z. B. HTTP-Client schließen).
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
    // Herzstück: öffnet die WS (sobald jemand den Flow sammelt) und liefert alle Server-Events als Flow.
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

    // erzeugt ein "Warte-Handle" (CompletableDeferred = Versprechen, auf das man await()en kann),
    // auf das awaitConnected() wartet; setzt außerdem die Status-Flags zurück.
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

    // Verbindung steht: aktive Session merken (current) und das Warte-Handle erfüllen
    // (ready.complete) -> alle, die in awaitConnected() warten, werden freigegeben.
    private fun markConnected(
        opened: OpenedSession,
        ready: CompletableDeferred<Unit>,
    ) {
        current = opened
        connectionFailure = null
        ready.complete(Unit)
    }

    /** Continuously reads from the session's incoming channel and emits decoded envelopes. */
    // FlowCollector = der "Ausgang" des Flows, auf den man per emit() Events legt. Diese Funktion
    // läuft im Flow, schiebt eingehende Events raus und meldet unerwartetes Schließen als Fehler.
    private suspend fun FlowCollector<WebSocketEnvelope>.emitIncomingEnvelopes(
        opened: OpenedSession,
    ) {
        val closeDetails = consumeIncomingFrames(opened)
        if (current === opened && !isIntentionalDisconnect) {
            error(closeDetails ?: "WebSocket connection lost unexpectedly")
        }
    }

    /** Iterates over incoming frames, handling close messages and extracting text data. */
    // liest eingehende Frames: Close -> Grund merken; Text -> zu Envelope decodieren und emitten.
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
    // ein WS-Frame kann Text/Binär/Ping/Close sein. Hier: nur Text-Frames -> JSON zu einem
    // WebSocketEnvelope parsen; kein Text oder Parse-Fehler -> null (wird übersprungen, kein Absturz).
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

    // wartet, bis die Verbindung wirklich steht (oder wirft den Verbindungsfehler).
    suspend fun awaitConnected() {
        pendingConnection?.await()
            ?: connectionFailure?.let { throw it }
            ?: check(current != null) { "Not connected. Call connect() first." }
    }

    /** Sends CREATE_GAME. Returns the requestId so the caller can match the server reply well. */
    // Befehls-Bauart (gilt für alle Befehle): Payload bauen -> in Envelope mit requestId -> send(); requestId zurück.
    suspend fun createGame(playerName: String): String {
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
        playerName: String,
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
                ResolveAuctionPayload.serializer(),
                ResolveAuctionPayload(buyBack = buyBack),
            )
        send(WebSocketEnvelope(WebSocketType.RESOLVE_AUCTION, requestId, payload))
        return requestId
    }

    /** Starts a "Kuhhandel" trade with another player. */
    suspend fun initiateTrade(
        challengedPlayerId: String,
        animalType: at.aau.kuhhandel.shared.enums.AnimalType,
    ): String {
        val requestId = UUID.randomUUID().toString()
        val payload =
            WebSocketJson.json.encodeToJsonElement(
                ChooseTradePayload.serializer(),
                ChooseTradePayload(
                    challengedPlayerId = challengedPlayerId,
                    animalType = animalType,
                ),
            )
        send(WebSocketEnvelope(WebSocketType.CHOOSE_TRADE, requestId, payload))
        return requestId
    }

    /** Submits the auction buyer's selected money cards as payment. */
    suspend fun submitAuctionPayment(moneyCardIds: Set<String>): String {
        val requestId = UUID.randomUUID().toString()
        val payload =
            WebSocketJson.json.encodeToJsonElement(
                SubmitAuctionPaymentPayload.serializer(),
                SubmitAuctionPaymentPayload(moneyCardIds = moneyCardIds),
            )
        send(WebSocketEnvelope(WebSocketType.SUBMIT_AUCTION_PAYMENT, requestId, payload))
        return requestId
    }

    /** Submits the initiator's selected money cards. */
    suspend fun submitTradeMoney(moneyCardIds: Set<String>): String {
        val requestId = UUID.randomUUID().toString()
        val payload =
            WebSocketJson.json.encodeToJsonElement(
                SubmitTradeMoneyPayload.serializer(),
                SubmitTradeMoneyPayload(moneyCardIds = moneyCardIds),
            )
        send(WebSocketEnvelope(WebSocketType.SUBMIT_TRADE_MONEY, requestId, payload))
        return requestId
    }

    /** Accepts a trade offer or counters it with selected money cards. */
    suspend fun respondToTrade(counterOfferedMoneyCardIds: Set<String> = emptySet()): String {
        val requestId = UUID.randomUUID().toString()
        val payload =
            WebSocketJson.json.encodeToJsonElement(
                RespondToTradePayload.serializer(),
                RespondToTradePayload(
                    moneyCardIds = counterOfferedMoneyCardIds,
                ),
            )
        send(WebSocketEnvelope(WebSocketType.RESPOND_TO_TRADE, requestId, payload))
        return requestId
    }

    /** Initiates a spying action against an opponent player. */
    suspend fun spy(targetPlayerId: String): String {
        val requestId = UUID.randomUUID().toString()
        val payload =
            WebSocketJson.json.encodeToJsonElement(
                SpyPayload.serializer(),
                SpyPayload(
                    targetPlayerId = targetPlayerId,
                ),
            )
        send(WebSocketEnvelope(WebSocketType.SPY, requestId, payload))
        return requestId
    }

    /** Attempts to catch any players currently spying on this player. */
    suspend fun catchSpy(): String {
        val requestId = UUID.randomUUID().toString()
        send(WebSocketEnvelope(WebSocketType.CATCH_SPY, requestId))
        return requestId
    }

    /** Closes the current WebSocket connection and stops any active sessions. */
    // absichtliches Schließen: markiert isIntentionalDisconnect, damit der Flow es nicht als Fehler wertet.
    suspend fun disconnect() {
        isIntentionalDisconnect = true
        pendingConnection = null
        connectionFailure = null
        val active = current ?: return
        current = null
        active.close()
    }

    /** Serializes and sends an envelope over the active session. */
    // serialisiert den Envelope zu JSON und schickt ihn als Text-Frame über die aktive Session.
    private suspend fun send(envelope: WebSocketEnvelope) {
        val active = current?.session ?: error("Not connected. Call connect() first.")
        val text = WebSocketJson.json.encodeToString(WebSocketEnvelope.serializer(), envelope)
        active.send(Frame.Text(text))
    }

    /** Opens a new WebSocket session using the configured client factory and URL. */
    // öffnet die ECHTE Verbindung: HTTP-Client bauen -> Ktor-WS-Session zur WS-URL -> in OpenedSession
    // packen (mit Client-Cleanup). Bei Fehler Client schließen + werfen. (Im Test durch Fake ersetzbar.)
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
