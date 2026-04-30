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
    fun `bind stores the game id for a session`() {
        val sessionId = "session-1"
        val gameId = "game-1"

        registry.bind(sessionId, gameId)

        assertEquals(gameId, registry.gameIdFor(sessionId))
    }

    @Test
    fun `unbind removes the mapping`() {
        val sessionId = "session-1"
        val gameId = "game-1"
        registry.bind(sessionId, gameId)

        registry.unbind(sessionId)

        assertNull(registry.gameIdFor(sessionId))
    }
}
