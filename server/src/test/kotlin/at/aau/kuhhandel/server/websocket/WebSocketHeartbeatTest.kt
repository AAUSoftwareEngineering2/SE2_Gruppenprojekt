package at.aau.kuhhandel.server.websocket

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.WebSocketSession
import java.io.IOException
import org.mockito.Mockito.`when` as whenever

class WebSocketHeartbeatTest {
    private lateinit var registry: ConnectionRegistry
    private lateinit var heartbeat: WebSocketHeartbeat

    @BeforeEach
    fun setUp() {
        registry = ConnectionRegistry()
        heartbeat = WebSocketHeartbeat(registry)
    }

    @Test
    // testet: dass an jede offene WebSocket-Session eine Ping-Nachricht gesendet wird
    fun `sendHeartbeats pings every open session`() {
        val session1 = openSession("session-1")
        val session2 = openSession("session-2")
        registry.bindConnection(session1)
        registry.bindConnection(session2)

        heartbeat.sendHeartbeats()

        verify(session1).sendMessage(any(PingMessage::class.java))
        verify(session2).sendMessage(any(PingMessage::class.java))
    }

    @Test
    // testet: dass eine geschlossene Session aus der Registry entfernt und nicht angepingt wird
    fun `sendHeartbeats evicts a closed session without pinging`() {
        val session = mock(WebSocketSession::class.java)
        whenever(session.id).thenReturn("session-closed")
        whenever(session.isOpen).thenReturn(false)
        registry.bindConnection(session)

        heartbeat.sendHeartbeats()

        verify(session, never()).sendMessage(any())
        assert(registry.connectionFor(session.id) == null) {
            "Closed session must be evicted from the registry"
        }
    }

    @Test
    // testet: dass eine Session, deren Senden eine IOException wirft, aus der Registry entfernt wird
    fun `sendHeartbeats evicts a session that throws IOException on send`() {
        val session = openSession("session-broken")
        whenever(session.sendMessage(any(PingMessage::class.java)))
            .thenThrow(IOException("broken pipe"))
        registry.bindConnection(session)

        heartbeat.sendHeartbeats()

        assert(registry.connectionFor(session.id) == null) {
            "Session whose send throws must be evicted from the registry"
        }
    }

    @Test
    // testet: dass bei leerer Registry keine Aktion erfolgt und keine Exception geworfen wird
    fun `sendHeartbeats with no sessions does nothing and does not throw`() {
        heartbeat.sendHeartbeats()
        // No exception means pass; nothing to verify on an empty registry.
    }

    private fun openSession(id: String): WebSocketSession {
        val session = mock(WebSocketSession::class.java)
        whenever(session.id).thenReturn(id)
        whenever(session.isOpen).thenReturn(true)
        return session
    }
}
