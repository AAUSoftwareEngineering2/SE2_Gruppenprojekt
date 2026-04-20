package at.aau.kuhhandel.app.network.game

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
import kotlin.coroutines.CoroutineContext

/**
 * In-memory stand-in for Ktor's WebSocketSession. No real network, everything is a list or channel.
 * Tests use the helpers below; the `override`s at the bottom exist only because the interface demands them.
 */
internal class FakeWebSocketSession : WebSocketSession {
    val sentFrames = mutableListOf<Frame>()
    val wasClosed: Boolean get() = sentFrames.any { it is Frame.Close }

    fun deliverText(text: String) {
        incomingChannel.trySend(Frame.Text(text))
    }

    fun deliverEnvelope(envelope: WebSocketEnvelope) {
        deliverText(WebSocketJson.json.encodeToString(WebSocketEnvelope.serializer(), envelope))
    }

    fun deliverEnvelope(
        type: WebSocketType,
        requestId: String?,
    ) {
        deliverEnvelope(WebSocketEnvelope(type = type, requestId = requestId))
    }

    fun deliverClose(
        code: CloseReason.Codes = CloseReason.Codes.NORMAL,
        message: String = "",
    ) {
        incomingChannel.trySend(Frame.Close(CloseReason(code, message)))
    }

    fun closeIncoming() {
        incomingChannel.close()
    }

    fun onlySentEnvelope(): WebSocketEnvelope {
        val text = sentFrames.filterIsInstance<Frame.Text>().single()
        return WebSocketJson.json.decodeFromString(WebSocketEnvelope.serializer(), text.readText())
    }

    fun sentEnvelopes(): List<WebSocketEnvelope> =
        sentFrames
            .filterIsInstance<Frame.Text>()
            .map {
                WebSocketJson.json.decodeFromString(
                    WebSocketEnvelope.serializer(),
                    it.readText(),
                )
            }

    fun sentTypes(): List<WebSocketType> = sentEnvelopes().map { it.type }

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
