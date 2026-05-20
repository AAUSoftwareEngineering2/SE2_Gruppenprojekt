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
}
