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

    @Test
    fun `getSessionsForGame returns all sessions for a specific game`() {
        val game1 = "G1"
        val game2 = "G2"

        val s1 = mockk<WebSocketSession>()
        every { s1.id } returns "s1"
        val s2 = mockk<WebSocketSession>()
        every { s2.id } returns "s2"
        val s3 = mockk<WebSocketSession>()
        every { s3.id } returns "s3"

        registry.bind(s1, game1)
        registry.bind(s2, game1)
        registry.bind(s3, game2)

        val sessionsG1 = registry.getSessionsForGame(game1)
        assertEquals(2, sessionsG1.size)
        assertEquals(setOf(s1, s2), sessionsG1.toSet())

        val sessionsG2 = registry.getSessionsForGame(game2)
        assertEquals(1, sessionsG2.size)
        assertEquals(s3, sessionsG2[0])
    }

    @Test
    fun `getSessionsForGame returns empty list if no sessions for game`() {
        val sessions = registry.getSessionsForGame("unknown")
        assertEquals(0, sessions.size)
    }
}
