package at.aau.kuhhandel.shared.enums

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GameErrorReasonTest {
    @Test
    fun `PLAYER_EXCLUDED_FROM_AUCTION has stable serialized name`() {
        assertEquals(
            "EXCLUDED_FROM_AUCTION",
            GameErrorReason.EXCLUDED_FROM_AUCTION.name,
        )
    }

    @Test
    fun `all error reasons provide a non-empty user message`() {
        GameErrorReason.entries.forEach { reason ->
            val message = reason.toUserMessage()
            assertTrue(message.isNotEmpty(), "Message for $reason should not be empty")
        }
    }

    @Test
    fun `GAME_NOT_FOUND provides expected message`() {
        assertEquals(
            "Game not found. Please check the code.",
            GameErrorReason.GAME_NOT_FOUND.toUserMessage(),
        )
    }
}
