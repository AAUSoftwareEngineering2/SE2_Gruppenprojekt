package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.TradeStep
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
        assertEquals(TradeStep.WAITING_FOR_RESPONSE, state.step)
        assertEquals(0, state.offeredMoney)
        assertEquals(emptyList(), state.offeredMoneyCardIds)
        assertEquals(0, state.offeredMoneyCardCount)
        assertNull(state.counterOfferedMoney)
        assertEquals(emptyList(), state.counterOfferedMoneyCardIds)
        assertEquals(0, state.counterOfferedMoneyCardCount)
        assertFalse(state.isResolved)
    }

    @Test
    fun test_customTradeState() {
        val state =
            TradeState(
                initiatingPlayerId = "p3",
                challengedPlayerId = "p4",
                requestedAnimalType = AnimalType.DOG,
                step = TradeStep.RESOLVED,
                offeredMoney = 20,
                offeredMoneyCardIds = listOf("money-1"),
                offeredMoneyCardCount = 1,
                counterOfferedMoney = 30,
                counterOfferedMoneyCardIds = listOf("money-2"),
                counterOfferedMoneyCardCount = 1,
                isResolved = true,
            )

        assertEquals("p3", state.initiatingPlayerId)
        assertEquals("p4", state.challengedPlayerId)
        assertEquals(AnimalType.DOG, state.requestedAnimalType)
        assertEquals(TradeStep.RESOLVED, state.step)
        assertEquals(20, state.offeredMoney)
        assertEquals(listOf("money-1"), state.offeredMoneyCardIds)
        assertEquals(1, state.offeredMoneyCardCount)
        assertEquals(30, state.counterOfferedMoney)
        assertEquals(listOf("money-2"), state.counterOfferedMoneyCardIds)
        assertEquals(1, state.counterOfferedMoneyCardCount)
        assertTrue(state.isResolved)
    }
}
