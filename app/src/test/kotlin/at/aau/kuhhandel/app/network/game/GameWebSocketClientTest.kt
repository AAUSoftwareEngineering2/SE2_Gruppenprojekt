package at.aau.kuhhandel.app.network.game

import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameWebSocketClientTest {
    private lateinit var session: FakeWebSocketSession
    private lateinit var client: GameWebSocketClient

    @BeforeEach
    fun setUp() {
        session = FakeWebSocketSession()
        client = GameWebSocketClient { session }
    }

    @Test
    fun `connect twice throws`() =
        runBlocking {
            client.connect()
            assertFailsWith<IllegalStateException> { client.connect() }
            Unit
        }

    @Test
    fun `createGame without connect throws`() =
        runBlocking {
            assertFailsWith<IllegalStateException> { client.createGame("Fabio") }
            Unit
        }

    @Test
    fun `startGame without connect throws`() =
        runBlocking {
            assertFailsWith<IllegalStateException> { client.startGame() }
            Unit
        }

    @Test
    fun `revealCard without connect throws`() =
        runBlocking {
            assertFailsWith<IllegalStateException> { client.revealCard() }
            Unit
        }

    @Test
    fun `disconnect without connect does not throw`() =
        runBlocking {
            client.disconnect()
        }

    // Verifies the envelope sent over the wire: correct type, matching requestId, and serialized payload.
    @Test
    fun `createGame sends envelope with payload`() =
        runBlocking {
            client.connect()
            val requestId = client.createGame("Fabio")

            val envelope = session.onlySentEnvelope()
            assertEquals(WebSocketType.CREATE_GAME, envelope.type)
            assertEquals(requestId, envelope.requestId)

            val payload =
                WebSocketJson.json.decodeFromJsonElement(
                    CreateGamePayload.serializer(),
                    assertNotNull(envelope.payload),
                )
            assertEquals("Fabio", payload.playerName)
        }

    @Test
    fun `startGame sends envelope`() =
        runBlocking {
            client.connect()
            val requestId = client.startGame()

            val envelope = session.onlySentEnvelope()
            assertEquals(WebSocketType.START_GAME, envelope.type)
            assertEquals(requestId, envelope.requestId)
        }

    @Test
    fun `revealCard sends envelope`() =
        runBlocking {
            client.connect()
            val requestId = client.revealCard()

            val envelope = session.onlySentEnvelope()
            assertEquals(WebSocketType.REVEAL_CARD, envelope.type)
            assertEquals(requestId, envelope.requestId)
        }

    @Test
    fun `disconnect closes session`() =
        runBlocking {
            client.connect()
            client.disconnect()
            assertTrue(session.wasClosed)
        }

    // Simulates the server pushing a GAME_CREATED event and verifies the flow emits it to the collector.
    @Test
    fun `flow emits incoming envelopes`() =
        runBlocking {
            val events = client.connect()
            session.deliverEnvelope(WebSocketType.GAME_CREATED, "req-123")
            session.closeIncoming()

            val received = events.toList()
            assertEquals(1, received.size)
            assertEquals(WebSocketType.GAME_CREATED, received[0].type)
            assertEquals("req-123", received[0].requestId)
        }

    // Broken JSON from the server must not crash the collector — invalid frames are silently dropped.
    @Test
    fun `flow ignores malformed JSON`() =
        runBlocking {
            val events = client.connect()
            session.deliverText("not json")
            session.closeIncoming()

            assertEquals(0, events.toList().size)
        }

    @Test
    fun `flow completion closes session and allows reconnect`() =
        runBlocking {
            val firstSession = FakeWebSocketSession()
            val secondSession = FakeWebSocketSession()
            var openCount = 0
            val reconnectingClient =
                GameWebSocketClient {
                    if (openCount++ == 0) firstSession else secondSession
                }

            val firstEvents = reconnectingClient.connect()
            firstSession.closeIncoming()
            assertEquals(0, firstEvents.toList().size)
            assertTrue(firstSession.wasClosed)

            val secondEvents = reconnectingClient.connect()
            secondSession.closeIncoming()
            assertEquals(0, secondEvents.toList().size)
        }
}

/**
 * In-memory stand-in for Ktor's WebSocketSession. No real network, everything is a list or channel.
 * Tests use the helpers below; the `override`s at the bottom exist only because the interface demands them.
 */
private class FakeWebSocketSession : WebSocketSession {
    // --- What tests actually read/act on ---

    val sentFrames = mutableListOf<Frame>()
    val wasClosed: Boolean get() = sentFrames.any { it is Frame.Close }

    fun deliverText(text: String) {
        incomingChannel.trySend(Frame.Text(text))
    }

    fun deliverEnvelope(
        type: WebSocketType,
        requestId: String?,
    ) {
        val envelope = WebSocketEnvelope(type = type, requestId = requestId)
        deliverText(WebSocketJson.json.encodeToString(WebSocketEnvelope.serializer(), envelope))
    }

    fun closeIncoming() {
        incomingChannel.close()
    }

    fun onlySentEnvelope(): WebSocketEnvelope {
        val text = sentFrames.filterIsInstance<Frame.Text>().single()
        return WebSocketJson.json.decodeFromString(WebSocketEnvelope.serializer(), text.readText())
    }

    // --- WebSocketSession interface plumbing (required, not used by tests directly) ---

    private val incomingChannel = Channel<Frame>(Channel.UNLIMITED)

    override val coroutineContext: CoroutineContext = Dispatchers.Unconfined + Job()
    override val incoming: ReceiveChannel<Frame> = incomingChannel
    override val outgoing: SendChannel<Frame> = Channel(Channel.UNLIMITED)
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE
    override val extensions: List<WebSocketExtension<*>> = emptyList()

    override suspend fun send(frame: Frame) {
        sentFrames.add(frame)
        if (frame is Frame.Close) incomingChannel.close()
    }

    override suspend fun flush() = Unit

    @Suppress("OVERRIDE_DEPRECATION")
    override fun terminate() {
        incomingChannel.close()
    }
}
