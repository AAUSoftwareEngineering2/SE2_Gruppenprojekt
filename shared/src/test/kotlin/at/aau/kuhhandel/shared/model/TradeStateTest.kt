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
                initiatorId = "p1",
                targetId = "p2",
                requestedAnimalType = AnimalType.COW,
            )

        assertEquals("p1", state.initiatorId)
        assertEquals("p2", state.targetId)
        assertEquals(AnimalType.COW, state.requestedAnimalType)
        assertEquals(0, state.offeredMoney)
        assertEquals(emptySet(), state.offeredMoneyCardIds)
        assertEquals(0, state.offeredMoneyCardIds.size)
        assertNull(state.counterOfferedMoney)
        assertEquals(emptySet(), state.counterOfferedMoneyCardIds)
        assertEquals(0, state.counterOfferedMoneyCardIds.size)
        assertFalse(state.isResolved)
    }

    @Test
    fun test_customTradeState() {
        val state =
            TradeState(
                initiatorId = "p3",
                targetId = "p4",
                requestedAnimalType = AnimalType.DOG,
                offeredMoney = 20,
                offeredMoneyCardIds = setOf("money-1"),
                counterOfferedMoney = 30,
                counterOfferedMoneyCardIds = setOf("money-2"),
                isResolved = true,
            )

        assertEquals("p3", state.initiatorId)
        assertEquals("p4", state.targetId)
        assertEquals(AnimalType.DOG, state.requestedAnimalType)
        assertEquals(20, state.offeredMoney)
        assertEquals(setOf("money-1"), state.offeredMoneyCardIds)
        assertEquals(1, state.offeredMoneyCardIds.size)
        assertEquals(30, state.counterOfferedMoney)
        assertEquals(setOf("money-2"), state.counterOfferedMoneyCardIds)
        assertEquals(1, state.counterOfferedMoneyCardIds.size)
        assertTrue(state.isResolved)
    }
}
