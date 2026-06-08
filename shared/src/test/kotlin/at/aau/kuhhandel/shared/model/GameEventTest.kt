package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.websocket.WebSocketJson
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameEventTest {
    @Test
    fun `MoneyBonus can be instantiated and serialized`() {
        val event = GameEvent.MoneyBonus(amount = 100, message = "Bonus!")
        assertEquals(100, event.amount)
        assertEquals("Bonus!", event.message)

        val json = WebSocketJson.json.encodeToString<GameEvent>(event)
        assertTrue(json.contains("\"amount\":100"))
        assertTrue(json.contains("\"message\":\"Bonus!\""))

        val decoded = WebSocketJson.json.decodeFromString<GameEvent>(json)
        assertTrue(decoded is GameEvent.MoneyBonus)
        assertEquals(100, decoded.amount)
    }

    @Test
    fun `BluffDetected can be instantiated and serialized`() {
        val event =
            GameEvent.BluffDetected(
                playerId = "player-2",
                playerName = "Player 2",
                message = "Player 2 bluffed!",
            )

        assertEquals("player-2", event.playerId)
        assertEquals("Player 2", event.playerName)
        assertEquals("Player 2 bluffed!", event.message)

        val json = WebSocketJson.json.encodeToString<GameEvent>(event)
        assertTrue(json.contains("\"playerId\":\"player-2\""))
        assertTrue(json.contains("\"playerName\":\"Player 2\""))
        assertTrue(json.contains("\"message\":\"Player 2 bluffed!\""))

        val decoded = WebSocketJson.json.decodeFromString<GameEvent>(json)
        assertTrue(decoded is GameEvent.BluffDetected)
        assertEquals("player-2", decoded.playerId)
        assertEquals("Player 2", decoded.playerName)
        assertEquals("Player 2 bluffed!", decoded.message)
    }
}
