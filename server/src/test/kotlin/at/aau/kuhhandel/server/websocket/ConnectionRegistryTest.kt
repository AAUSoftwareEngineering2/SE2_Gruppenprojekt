package at.aau.kuhhandel.server.websocket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConnectionRegistryTest {
    private lateinit var registry: ConnectionRegistry

    @BeforeEach
    fun setUp() {
        registry = ConnectionRegistry()
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
        val sessionId1 = "session-1"
        val playerId1 = "player-1"
        val sessionId2 = "session-2"
        val playerId2 = "player-2"
        val gameId = "game-1"
        registry.bindGame(sessionId1, gameId)
        registry.bindPlayer(sessionId1, playerId1)
        registry.bindGame(sessionId2, gameId)
        registry.bindPlayer(sessionId2, playerId2)

        registry.unbind(sessionId1)

        assertNull(registry.gameIdFor(sessionId1))
        assertNull(registry.playerIdFor(sessionId1))
        assertEquals(setOf(sessionId2), registry.sessionIdsFor(gameId))
    }
}
