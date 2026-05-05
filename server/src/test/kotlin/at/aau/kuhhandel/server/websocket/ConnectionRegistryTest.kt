package at.aau.kuhhandel.server.websocket

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.socket.WebSocketSession

class ConnectionRegistryTest {
    private lateinit var registry: ConnectionRegistry

    @BeforeEach
    fun setUp() {
        registry = ConnectionRegistry()
    }

    @Test
    fun `bind stores the game id for a session`() {
        val sessionId = "session-1"
        val gameId = "game-1"
        val session = mockk<WebSocketSession>()
        every { session.id } returns sessionId

        registry.bind(session, gameId)

        assertEquals(gameId, registry.gameIdFor(sessionId))
    }

    @Test
    fun `unbind removes the mapping`() {
        val sessionId = "session-1"
        val gameId = "game-1"
        val session = mockk<WebSocketSession>()
        every { session.id } returns sessionId
        registry.bind(session, gameId)

        registry.unbind(sessionId)

        assertNull(registry.gameIdFor(sessionId))
    }
}
