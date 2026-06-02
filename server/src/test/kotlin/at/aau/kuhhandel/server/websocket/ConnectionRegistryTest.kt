package at.aau.kuhhandel.server.websocket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.mockito.Mockito.mock
import org.springframework.web.socket.WebSocketSession
import org.mockito.Mockito.`when` as whenever

class ConnectionRegistryTest {
    private lateinit var registry: ConnectionRegistry

    @BeforeEach
    fun setUp() {
        registry = ConnectionRegistry()
    }

    @Test
    fun `bindSession stores mapping`() {
        val sessionId = "session-1"
        val session = mock(WebSocketSession::class.java)
        whenever(session.id).thenReturn(sessionId)

        registry.bindSession(session)

        assertEquals(session, registry.sessionFor(sessionId))
    }

    @Test
    fun `bindPlayerSession stores mapping`() {
        val sessionId1 = "session-1"
        val playerId1 = "player-1"
        val sessionId2 = "session-2"
        val playerId2 = "player-2"
        val gameId = "game-1"

        registry.bindPlayerSession(sessionId1, gameId, playerId1)
        registry.bindPlayerSession(sessionId2, gameId, playerId2)

        val playerSession1 = registry.playerSessionFor(sessionId1)
        assertNotNull(playerSession1)
        assertEquals(gameId, playerSession1.gameId)
        assertEquals(playerId1, playerSession1.playerId)

        val playerSession2 = registry.playerSessionFor(sessionId2)
        assertNotNull(playerSession2)
        assertEquals(gameId, playerSession2.gameId)
        assertEquals(playerId2, playerSession2.playerId)

        assertEquals(setOf(sessionId1, sessionId2), registry.sessionIdsFor(gameId))
    }

    @Test
    fun `sessionsFor returns all sessions for a specific game`() {
        val session1 = mock(WebSocketSession::class.java)
        whenever(session1.id).thenReturn("session-1")
        val session2 = mock(WebSocketSession::class.java)
        whenever(session2.id).thenReturn("session-2")
        val session3 = mock(WebSocketSession::class.java)
        whenever(session3.id).thenReturn("session-3")

        val gameId1 = "game-1"
        val gameId2 = "game-2"
        val playerId1 = "player-1"
        val playerId2 = "player-2"
        val playerId3 = "player-3"

        registry.bindSession(session1)
        registry.bindPlayerSession(session1.id, gameId1, playerId1)
        registry.bindSession(session2)
        registry.bindPlayerSession(session2.id, gameId1, playerId2)
        registry.bindSession(session3)
        registry.bindPlayerSession(session3.id, gameId2, playerId3)

        val game1Sessions = registry.sessionsFor(gameId1)
        assertEquals(2, game1Sessions.size)
        assertEquals(setOf(session1, session2), game1Sessions.toSet())

        val game2Sessions = registry.sessionsFor(gameId2)
        assertEquals(1, game2Sessions.size)
        assertEquals(setOf(session3), game2Sessions)
    }

    @Test
    fun `sessionsFor returns empty list if no sessions for game`() {
        val sessions = registry.sessionsFor("unknown")

        assertEquals(0, sessions.size)
    }

    @Test
    fun `allSessions returns snapshot of all bound sessions`() {
        val session1 = mock(WebSocketSession::class.java)
        whenever(session1.id).thenReturn("session-1")
        val session2 = mock(WebSocketSession::class.java)
        whenever(session2.id).thenReturn("session-2")

        registry.bindSession(session1)
        registry.bindSession(session2)

        assertEquals(setOf(session1, session2), registry.allSessions().toSet())
    }

    @Test
    fun `allSessions returns empty when no sessions are bound`() {
        assertEquals(emptyList<WebSocketSession>(), registry.allSessions().toList())
    }

    @Test
    fun `allSessions reflects unbinds`() {
        val session = mock(WebSocketSession::class.java)
        whenever(session.id).thenReturn("session-1")
        registry.bindSession(session)

        registry.unbind(session.id)

        assertEquals(emptyList<WebSocketSession>(), registry.allSessions().toList())
    }

    @Test
    fun `unbind removes the mapping`() {
        val session1 = mock(WebSocketSession::class.java)
        whenever(session1.id).thenReturn("session-1")
        val playerId1 = "player-1"
        val session2 = mock(WebSocketSession::class.java)
        whenever(session2.id).thenReturn("session-2")
        val playerId2 = "player-2"
        val gameId = "game-1"
        registry.bindSession(session1)
        registry.bindPlayerSession(session1.id, gameId, playerId1)
        registry.bindSession(session2)
        registry.bindPlayerSession(session2.id, gameId, playerId2)

        registry.unbind(session1.id)

        assertNull(registry.playerSessionFor(session1.id))
        assertEquals(setOf(session2.id), registry.sessionIdsFor(gameId))
        assertEquals(setOf(session2), registry.sessionsFor(gameId))
    }
}
