package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TradeStateTest {
    @Test
    fun test_defaultTradeState() {
        val state =
            TradeState(
                initiatingPlayerId = "p1",
                challengedPlayerId = "p2",
                requestedAnimalType = AnimalType.COW,
            )

        assertEquals("p1", state.initiatingPlayerId)
        assertEquals("p2", state.challengedPlayerId)
        assertEquals(AnimalType.COW, state.requestedAnimalType)
        assertEquals(0, state.offeredMoney)
        assertNull(state.counterOfferedMoney)
        assertFalse(state.isResolved)
    }
}
