package at.aau.kuhhandel.server.websocket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    fun `bindGame with unbound sessions stores mapping`() {
        val sessionId1 = "session-1"
        val sessionId2 = "session-2"
        val gameId = "game-1"

        registry.bindGame(sessionId1, gameId)
        registry.bindGame(sessionId2, gameId)

        assertEquals(gameId, registry.gameIdFor(sessionId1))
        assertEquals(gameId, registry.gameIdFor(sessionId2))
        assertEquals(setOf(sessionId1, sessionId2), registry.sessionIdsFor(gameId))
    }

    @Test
    fun `bindGame with bound session does not store mapping`() {
        val sessionId = "session-1"
        val gameId1 = "game-1"
        val gameId2 = "game-2"
        registry.bindGame(sessionId, gameId1)

        registry.bindGame(sessionId, gameId2)

        assertEquals(gameId1, registry.gameIdFor(sessionId))
        assertEquals(setOf<String>(), registry.sessionIdsFor(gameId2))
    }

    @Test
    fun `bindPlayer with unbound sessions stores mapping`() {
        val sessionId = "session-1"
        val playerId = "player-1"

        registry.bindPlayer(sessionId, playerId)

        assertEquals(playerId, registry.playerIdFor(sessionId))
    }

    @Test
    fun `bindPlayer with bound session does not store mapping`() {
        val sessionId = "session-1"
        val playerId1 = "player-1"
        val playerId2 = "player-2"
        registry.bindPlayer(sessionId, playerId1)

        registry.bindPlayer(sessionId, playerId2)

        assertEquals(playerId1, registry.playerIdFor(sessionId))
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
        registry.bindGame(session1.id, gameId)
        registry.bindPlayer(session1.id, playerId1)
        registry.bindSession(session2)
        registry.bindGame(session2.id, gameId)
        registry.bindPlayer(session2.id, playerId2)

        registry.unbind(session1.id)

        assertNull(registry.gameIdFor(session1.id))
        assertNull(registry.playerIdFor(session1.id))
        assertEquals(setOf(session2.id), registry.sessionIdsFor(gameId))
        assertEquals(setOf(session2), registry.sessionsFor(gameId))
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

        registry.bindSession(session1)
        registry.bindGame(session1.id, gameId1)
        registry.bindSession(session2)
        registry.bindGame(session2.id, gameId1)
        registry.bindSession(session3)
        registry.bindGame(session3.id, gameId2)

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
}
