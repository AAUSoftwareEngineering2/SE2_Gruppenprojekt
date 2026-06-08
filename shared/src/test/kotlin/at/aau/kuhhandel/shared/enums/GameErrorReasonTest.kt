package at.aau.kuhhandel.shared.enums

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
}
