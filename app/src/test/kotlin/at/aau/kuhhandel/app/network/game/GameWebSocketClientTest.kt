package at.aau.kuhhandel.app.network.game

import at.aau.kuhhandel.shared.websocket.CreateGamePayload
import at.aau.kuhhandel.shared.websocket.WebSocketEnvelope
import at.aau.kuhhandel.shared.websocket.WebSocketJson
import at.aau.kuhhandel.shared.websocket.WebSocketType
import io.ktor.websocket.CloseReason
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
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameWebSocketClientTest {
    @Test
    fun `connect twice throws IllegalStateException`() {
        runBlocking {
            val client = GameWebSocketClient { FakeWebSocketSession() }
            client.connect()
            assertFailsWith<IllegalStateException> { client.connect() }
        }
    }

    @Test
    fun `createGame without connect throws IllegalStateException`() {
        runBlocking {
            val client = GameWebSocketClient { FakeWebSocketSession() }
            assertFailsWith<IllegalStateException> { client.createGame("Fabio") }
        }
    }

    @Test
    fun `startGame without connect throws IllegalStateException`() {
        runBlocking {
            val client = GameWebSocketClient { FakeWebSocketSession() }
            assertFailsWith<IllegalStateException> { client.startGame() }
        }
    }

    @Test
    fun `revealCard without connect throws IllegalStateException`() {
        runBlocking {
            val client = GameWebSocketClient { FakeWebSocketSession() }
            assertFailsWith<IllegalStateException> { client.revealCard() }
        }
    }

    @Test
    fun `disconnect without connect does not throw`() {
        runBlocking {
            val client = GameWebSocketClient { FakeWebSocketSession() }
            client.disconnect()
        }
    }

    @Test
    fun `createGame sends CREATE_GAME envelope with playerName payload`() {
        runBlocking {
            val session = FakeWebSocketSession()
            val client = GameWebSocketClient { session }
            client.connect()

            val requestId = client.createGame("Fabio")

            val envelope = session.decodeSingleSent()
            assertEquals(WebSocketType.CREATE_GAME, envelope.type)
            assertEquals(requestId, envelope.requestId)

            val payload =
                WebSocketJson.json.decodeFromJsonElement(
                    CreateGamePayload.serializer(),
                    assertNotNull(envelope.payload),
                )
            assertEquals("Fabio", payload.playerName)
        }
    }

    @Test
    fun `startGame sends START_GAME envelope without payload`() {
        runBlocking {
            val session = FakeWebSocketSession()
            val client = GameWebSocketClient { session }
            client.connect()

            val requestId = client.startGame()

            val envelope = session.decodeSingleSent()
            assertEquals(WebSocketType.START_GAME, envelope.type)
            assertEquals(requestId, envelope.requestId)
        }
    }

    @Test
    fun `revealCard sends REVEAL_CARD envelope`() {
        runBlocking {
            val session = FakeWebSocketSession()
            val client = GameWebSocketClient { session }
            client.connect()

            val requestId = client.revealCard()

            val envelope = session.decodeSingleSent()
            assertEquals(WebSocketType.REVEAL_CARD, envelope.type)
            assertEquals(requestId, envelope.requestId)
        }
    }

    @Test
    fun `disconnect closes the session`() {
        runBlocking {
            val session = FakeWebSocketSession()
            val client = GameWebSocketClient { session }
            client.connect()

            client.disconnect()

            assertTrue(session.wasClosed)
        }
    }

    @Test
    fun `connect flow emits incoming envelopes`() {
        runBlocking {
            val session = FakeWebSocketSession()
            val client = GameWebSocketClient { session }
            val events = client.connect()

            val incoming =
                WebSocketEnvelope(
                    type = WebSocketType.GAME_CREATED,
                    requestId = "req-123",
                )
            session.emitIncomingText(
                WebSocketJson.json.encodeToString(
                    WebSocketEnvelope.serializer(),
                    incoming,
                ),
            )
            session.closeIncoming()

            val received = events.toList()
            assertEquals(1, received.size)
            assertEquals(WebSocketType.GAME_CREATED, received[0].type)
            assertEquals("req-123", received[0].requestId)
        }
    }

    @Test
    fun `connect flow ignores malformed JSON frames`() {
        runBlocking {
            val session = FakeWebSocketSession()
            val client = GameWebSocketClient { session }
            val events = client.connect()

            session.emitIncomingText("not valid json")
            session.closeIncoming()

            val received = events.toList()
            assertEquals(0, received.size)
        }
    }

    private fun FakeWebSocketSession.decodeSingleSent(): WebSocketEnvelope {
        assertEquals(1, sentFrames.size)
        val frame = sentFrames.single()
        assertTrue(frame is Frame.Text)
        return WebSocketJson.json.decodeFromString(
            WebSocketEnvelope.serializer(),
            frame.readText(),
        )
    }
}

private class FakeWebSocketSession : WebSocketSession {
    val sentFrames = mutableListOf<Frame>()
    var wasClosed: Boolean = false
        private set

    private val incomingChannel = Channel<Frame>(Channel.UNLIMITED)

    override val coroutineContext: CoroutineContext = Dispatchers.Unconfined + Job()
    override val incoming: ReceiveChannel<Frame> = incomingChannel
    override val outgoing: SendChannel<Frame> = Channel(Channel.UNLIMITED)
    override var maxFrameSize: Long = Long.MAX_VALUE
    override val extensions: List<WebSocketExtension<*>> = emptyList()

    override suspend fun send(frame: Frame) {
        sentFrames.add(frame)
    }

    override suspend fun flush() = Unit

    override fun terminate() {
        wasClosed = true
        incomingChannel.close()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override suspend fun close(cause: CloseReason?) {
        wasClosed = true
        incomingChannel.close()
    }

    fun emitIncomingText(text: String) {
        incomingChannel.trySend(Frame.Text(text))
    }

    fun closeIncoming() {
        incomingChannel.close()
    }
}
