package at.aau.kuhhandel.app.network.game

import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.websocket.websocketServerAccept
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.coroutines.coroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameWebSocketClientTest {
    private data class ConnectedClient(
        val client: GameWebSocketClient,
        val session: FakeWebSocketSession,
        private val collector: Job,
    ) {
        suspend fun disconnect() {
            client.disconnect()
            collector.join()
        }
    }

    private lateinit var session: FakeWebSocketSession
    private lateinit var client: GameWebSocketClient

    @BeforeEach
    fun setUp() {
        session = FakeWebSocketSession()
        client = testClient(session)
    }

    private fun testClient(
        session: FakeWebSocketSession,
        extraCleanup: suspend () -> Unit = {},
    ): GameWebSocketClient =
        GameWebSocketClient(
            openSession = { OpenedSession(session, extraCleanup) },
        )

    private fun defaultOpenSessionClient(
        session: FakeWebSocketSession,
        onUrlSeen: (String) -> Unit,
    ): GameWebSocketClient =
        GameWebSocketClient(
            clientFactory = {
                HttpClient(MockEngine) {
                    install(WebSockets)
                    engine {
                        addHandler { request ->
                            onUrlSeen(request.url.toString())
                            HttpResponseData(
                                statusCode = HttpStatusCode.SwitchingProtocols,
                                requestTime = GMTDate(),
                                headers =
                                    headersOf(
                                        HttpHeaders.SecWebSocketAccept,
                                        websocketServerAccept(
                                            requireNotNull(
                                                request.body.headers[HttpHeaders.SecWebSocketKey],
                                            ),
                                        ),
                                    ),
                                version = HttpProtocolVersion.HTTP_1_1,
                                body = session,
                                callContext = coroutineContext,
                            )
                        }
                    }
                }
            },
            webSocketUrl = "ws://localhost/test",
        )

    private suspend fun CoroutineScope.connectClient(
        target: GameWebSocketClient = client,
        targetSession: FakeWebSocketSession = session,
    ): ConnectedClient {
        val collector =
            launch(start = CoroutineStart.UNDISPATCHED) {
                target.connect().collect()
            }
        target.awaitConnected()
        return ConnectedClient(target, targetSession, collector)
    }

    private suspend fun CoroutineScope.collectEvents(
        target: GameWebSocketClient = client,
    ): Deferred<List<WebSocketEnvelope>> {
        val events =
            async(start = CoroutineStart.UNDISPATCHED) {
                target.connect().toList()
            }
        target.awaitConnected()
        return events
    }

    // === Negativ-Tests: alles, was Verbindung braucht, muss ohne Connect knallen ===

    @Test
    fun `connect twice throws`() =
        runBlocking {
            client.connect()
            assertFailsWith<IllegalStateException> { client.connect() }
            client.disconnect()
        }

    @Test
    fun `awaitConnected without connect throws`() =
        runBlocking {
            assertFailsWith<IllegalStateException> { client.awaitConnected() }
        }

    @Test
    fun `createGame without connect throws`() =
        runBlocking {
            assertFailsWith<IllegalStateException> { client.createGame("Fabio") }
        }

    @Test
    fun `startGame without connect throws`() =
        runBlocking {
            assertFailsWith<IllegalStateException> { client.startGame() }
        }

    @Test
    fun `revealCard without connect throws`() =
        runBlocking {
            assertFailsWith<IllegalStateException> { client.revealCard() }
        }

    @Test
    fun `disconnect without connect does not throw`() =
        runBlocking {
            client.disconnect()
        }

    // === Send-Tests: was schickt der Client an den Server? ===

    @Test
    fun `createGame sends envelope with payload`() =
        runBlocking {
            // ARRANGE: Verbindung aufbauen
            val connection = connectClient()

            // ACT: Befehl ausloesen
            client.createGame("Fabio")

            // ASSERT: Envelope-Typ und Payload pruefen
            val payload =
                WebSocketJson.json.decodeFromJsonElement(
                    CreateGamePayload.serializer(),
                    assertNotNull(connection.session.onlySentEnvelope().payload),
                )

            assertEquals(WebSocketType.CREATE_GAME, connection.session.onlySentEnvelope().type)
            assertEquals("Fabio", payload.playerName)

            // CLEANUP: Verbindung sauber zumachen
            connection.disconnect()
        }

    @Test
    fun `startGame sends envelope`() =
        runBlocking {
            val connection = connectClient()

            val requestId = client.startGame()
            val sent = connection.session.onlySentEnvelope()

            assertEquals(WebSocketType.START_GAME, sent.type)
            assertEquals(requestId, sent.requestId)

            connection.disconnect()
        }

    @Test
    fun `revealCard sends envelope`() =
        runBlocking {
            val connection = connectClient()

            val requestId = client.revealCard()
            val sent = connection.session.onlySentEnvelope()

            assertEquals(WebSocketType.REVEAL_CARD, sent.type)
            assertEquals(requestId, sent.requestId)

            connection.disconnect()
        }

    @Test
    fun `disconnect closes session`() =
        runBlocking {
            val connection = connectClient()

            connection.disconnect()

            assertTrue(connection.session.wasClosed)
        }

    // === Receive-Tests: was passiert mit eingehenden Server-Nachrichten? ===

    @Test
    fun `flow emits incoming envelopes`() =
        runBlocking {
            val events = collectEvents()

            session.deliverEnvelope(WebSocketType.GAME_CREATED, "req-123")
            session.closeIncoming()

            val received = events.await()
            assertEquals(1, received.size)
            assertEquals(WebSocketType.GAME_CREATED, received[0].type)
            assertEquals("req-123", received[0].requestId)
        }

    @Test
    fun `flow ignores malformed JSON`() =
        runBlocking {
            val events = collectEvents()

            session.deliverText("not json")
            session.closeIncoming()

            assertEquals(0, events.await().size)
        }

    // === Lifecycle-Tests: Cleanup, Reconnect, Retry ===

    @Test
    fun `flow completion closes session and allows reconnect`() =
        runBlocking {
            val firstSession = FakeWebSocketSession()
            val secondSession = FakeWebSocketSession()
            val sessions = listOf(firstSession, secondSession)
            var openCount = 0
            val reconnectingClient =
                GameWebSocketClient(
                    openSession = { OpenedSession(sessions[openCount++]) },
                )

            val firstEvents = collectEvents(reconnectingClient)
            firstSession.closeIncoming()
            assertEquals(0, firstEvents.await().size)
            assertTrue(firstSession.wasClosed)

            val secondEvents = collectEvents(reconnectingClient)
            secondSession.closeIncoming()
            assertEquals(0, secondEvents.await().size)
        }

    @Test
    fun `flow completion invokes extra cleanup`() =
        runBlocking {
            var cleanupCalls = 0
            val customClient =
                testClient(session) {
                    cleanupCalls++
                }

            val events = collectEvents(customClient)
            session.closeIncoming()
            events.await()

            assertEquals(1, cleanupCalls)
        }

    @Test
    fun `disconnect invokes extra cleanup`() =
        runBlocking {
            var cleanupCalls = 0
            val customClient =
                testClient(session) {
                    cleanupCalls++
                }

            val connection = connectClient(customClient)
            connection.disconnect()

            assertEquals(1, cleanupCalls)
        }

    @Test
    fun `extra cleanup exception does not bubble up`() =
        runBlocking {
            val customClient =
                testClient(session) {
                    error("boom")
                }

            val connection = connectClient(customClient)
            connection.disconnect()
        }

    @Test
    fun `failed connection allows a later retry`() =
        runBlocking {
            val retrySession = FakeWebSocketSession()
            var shouldFail = true
            val retryClient =
                GameWebSocketClient(
                    openSession = {
                        if (shouldFail) {
                            shouldFail = false
                            throw IllegalStateException("offline")
                        }
                        OpenedSession(retrySession)
                    },
                )

            val failedCollector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    assertFailsWith<IllegalStateException> {
                        retryClient.connect().collect()
                    }
                }

            assertFailsWith<IllegalStateException> { retryClient.awaitConnected() }
            failedCollector.join()

            val connection = connectClient(retryClient, retrySession)
            connection.disconnect()

            assertTrue(retrySession.wasClosed)
        }

    // === Default-Code-Pfad: echte HttpClient-Factory mit MockEngine ===

    @Test
    fun `defaultOpenSession opens the configured websocket url`() =
        runBlocking {
            val defaultSession = FakeWebSocketSession()
            var seenUrl: String? = null
            val defaultClient =
                defaultOpenSessionClient(defaultSession) {
                    seenUrl = it
                }

            val connection = connectClient(defaultClient, defaultSession)

            assertTrue(seenUrl?.contains("/test") == true)

            connection.disconnect()
            assertTrue(defaultSession.wasClosed)
        }
}
