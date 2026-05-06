package at.aau.kuhhandel.server.websocket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConnectionRegistryTest {
    private lateinit var registry: ConnectionRegistry

    @BeforeEach
    fun setUp() {
        registry = ConnectionRegistry()
    }

    @Test
    fun `bind with unbound sessions stores mapping`() {
        val sessionId1 = "session-1"
        val sessionId2 = "session-2"
        val gameId = "game-1"

        assertTrue(registry.bind(sessionId1, gameId))
        assertTrue(registry.bind(sessionId2, gameId))

        assertEquals(gameId, registry.gameIdFor(sessionId1))
        assertEquals(gameId, registry.gameIdFor(sessionId2))
        assertEquals(setOf(sessionId1, sessionId2), registry.sessionIdsFor(gameId))
    }

    @Test
    fun `bind with bound session does not store mapping`() {
        val sessionId = "session-1"
        val gameId1 = "game-1"
        val gameId2 = "game-2"

        registry.bind(sessionId, gameId1)

        assertFalse(registry.bind(sessionId, gameId2))
        assertEquals(gameId1, registry.gameIdFor(sessionId))
        assertEquals(setOf<String>(), registry.sessionIdsFor(gameId2))
    }

    @Test
    fun `unbind removes the mapping`() {
        val sessionId1 = "session-1"
        val sessionId2 = "session-2"
        val gameId = "game-1"
        registry.bind(sessionId1, gameId)
        registry.bind(sessionId2, gameId)

        registry.unbind(sessionId1)

        assertNull(registry.gameIdFor(sessionId1))
        assertEquals(setOf(sessionId2), registry.sessionIdsFor(gameId))
    }
}
