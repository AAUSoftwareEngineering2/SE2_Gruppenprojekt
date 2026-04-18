package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun test_customTradeState() {
        val state =
            TradeState(
                initiatingPlayerId = "p3",
                challengedPlayerId = "p4",
                requestedAnimalType = AnimalType.DOG,
                offeredMoney = 20,
                counterOfferedMoney = 30,
                isResolved = true,
            )

        assertEquals("p3", state.initiatingPlayerId)
        assertEquals("p4", state.challengedPlayerId)
        assertEquals(AnimalType.DOG, state.requestedAnimalType)
        assertEquals(20, state.offeredMoney)
        assertEquals(30, state.counterOfferedMoney)
        assertTrue(state.isResolved)
    }
}
