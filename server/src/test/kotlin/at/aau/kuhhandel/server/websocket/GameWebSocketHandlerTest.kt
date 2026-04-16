package at.aau.kuhhandel.server.websocket

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

class GameWebSocketHandlerTest {
    private lateinit var handler: GameWebSocketHandler

    @BeforeEach
    fun setUp() {
        handler = GameWebSocketHandler()
    }

    @Test
    fun `after connection established sends CONNECTED`() {
        val session = mock(WebSocketSession::class.java)

        handler.afterConnectionEstablished(session)

        verify(session).sendMessage(TextMessage("CONNECTED"))
    }

    @Test
    fun `HELLO sends WELCOME`() {
        val session = mock(WebSocketSession::class.java)

        handler.handleMessage(session, TextMessage("HELLO"))

        verify(session).sendMessage(TextMessage("WELCOME"))
    }

    @Test
    fun `unknown message sends UNKNOWN`() {
        val session = mock(WebSocketSession::class.java)

        handler.handleMessage(session, TextMessage("anything else"))

        verify(session).sendMessage(TextMessage("UNKNOWN"))
    }
}
