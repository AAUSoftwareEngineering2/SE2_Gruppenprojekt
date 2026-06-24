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
// Test-Helfer: In-Memory-Fake einer Ktor-WebSocketSession ohne echtes Netzwerk; gesendete Frames landen in einer Liste, eingehende in einem Channel
internal class FakeWebSocketSession : WebSocketSession {
    val sentFrames = mutableListOf<Frame>()
    val wasClosed: Boolean get() = sentFrames.any { it is Frame.Close }

    // Test-Helfer: schiebt einen rohen Text-Frame in den Eingangs-Channel (simuliert Server-Nachricht)
    fun deliverText(text: String) {
        incomingChannel.trySend(Frame.Text(text))
    }

    // Test-Helfer: serialisiert ein Envelope zu JSON und liefert es als eingehende Nachricht aus
    fun deliverEnvelope(envelope: WebSocketEnvelope) {
        deliverText(WebSocketJson.json.encodeToString(WebSocketEnvelope.serializer(), envelope))
    }

    // Test-Helfer: baut aus Typ und requestId ein Envelope und liefert es als eingehende Nachricht aus
    fun deliverEnvelope(
        type: WebSocketType,
        requestId: String?,
    ) {
        deliverEnvelope(WebSocketEnvelope(type = type, requestId = requestId))
    }

    // Test-Helfer: liefert einen Close-Frame mit Code und Begruendung aus (simuliert Verbindungsabbruch durch Server)
    fun deliverClose(
        code: CloseReason.Codes = CloseReason.Codes.NORMAL,
        message: String = "",
    ) {
        incomingChannel.trySend(Frame.Close(CloseReason(code, message)))
    }

    fun deliverFrame(frame: Frame) {
        incomingChannel.trySend(frame)
    }

    // Test-Helfer: schliesst den Eingangs-Channel (simuliert sauberes Ende des Server-Streams)
    fun closeIncoming() {
        incomingChannel.close()
    }

    fun deliverError(cause: Throwable) {
        incomingChannel.close(cause)
    }

    // Test-Helfer: erwartet genau einen gesendeten Text-Frame und gibt ihn als dekodiertes Envelope zurueck
    fun onlySentEnvelope(): WebSocketEnvelope {
        val text = sentFrames.filterIsInstance<Frame.Text>().single()
        return WebSocketJson.json.decodeFromString(WebSocketEnvelope.serializer(), text.readText())
    }

    // Test-Helfer: dekodiert alle gesendeten Text-Frames zu einer Liste von Envelopes
    fun sentEnvelopes(): List<WebSocketEnvelope> =
        sentFrames
            .filterIsInstance<Frame.Text>()
            .map {
                WebSocketJson.json.decodeFromString(
                    WebSocketEnvelope.serializer(),
                    it.readText(),
                )
            }

    // Test-Helfer: liefert nur die WebSocketType-Werte aller gesendeten Envelopes
    fun sentTypes(): List<WebSocketType> = sentEnvelopes().map { it.type }

    private val incomingChannel = Channel<Frame>(Channel.UNLIMITED)

    override val coroutineContext: CoroutineContext = Dispatchers.Unconfined + Job()
    override val incoming: ReceiveChannel<Frame> = incomingChannel
    override val outgoing: SendChannel<Frame> = Channel(Channel.UNLIMITED)
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE
    override val extensions: List<WebSocketExtension<*>> = emptyList()

    // Test-Helfer: merkt gesendete Frames in der Liste und schliesst bei Close-Frame den Eingangs-Channel
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
