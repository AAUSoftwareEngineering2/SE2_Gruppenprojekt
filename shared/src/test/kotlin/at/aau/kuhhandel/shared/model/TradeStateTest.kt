package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TradeStateTest {
    @Test
    fun test_defaultTradeState() {
        val state =
            TradeState(
                initiatorId = "p1",
                targetId = "p2",
                animalCards = setOf(AnimalCard("trade-cow", AnimalType.COW)),
            )

        assertEquals("p1", state.initiatorId)
        assertEquals("p2", state.targetId)
        assertEquals(AnimalType.COW, state.animalCards.firstOrNull()?.type)
        assertEquals(0, (state.offeredMoneyCards?.sumOf { it.value } ?: 0))
        assertEquals(emptySet(), (state.offeredMoneyCards?.map { it.id }?.toSet() ?: emptySet()))
        assertEquals(0, (state.offeredMoneyCards?.map { it.id }?.toSet() ?: emptySet()).size)
        assertNull(state.counterOfferedMoneyCards?.sumOf { it.value })
        assertEquals(
            emptySet(),
            (
                state.counterOfferedMoneyCards?.map { it.id }?.toSet()
                    ?: emptySet()
            ),
        )
        assertEquals(0, (state.counterOfferedMoneyCards?.map { it.id }?.toSet() ?: emptySet()).size)
    }

    @Test
    fun test_customTradeState() {
        val state =
            TradeState(
                initiatorId = "p3",
                targetId = "p4",
                animalCards = setOf(AnimalCard("trade-dog", AnimalType.DOG)),
                offeredMoneyCards = setOf(MoneyCard("money-1", 20)),
                counterOfferedMoneyCards = setOf(MoneyCard("money-2", 30)),
            )

        assertEquals("p3", state.initiatorId)
        assertEquals("p4", state.targetId)
        assertEquals(AnimalType.DOG, state.animalCards.firstOrNull()?.type)
        assertEquals(20, (state.offeredMoneyCards?.sumOf { it.value } ?: 0))
        assertEquals(
            setOf("money-1"),
            (
                state.offeredMoneyCards?.map { it.id }?.toSet()
                    ?: emptySet()
            ),
        )
        assertEquals(1, (state.offeredMoneyCards?.map { it.id }?.toSet() ?: emptySet()).size)
        assertEquals(30, state.counterOfferedMoneyCards?.sumOf { it.value })
        assertEquals(
            setOf("money-2"),
            (
                state.counterOfferedMoneyCards?.map { it.id }?.toSet()
                    ?: emptySet()
            ),
        )
        assertEquals(1, (state.counterOfferedMoneyCards?.map { it.id }?.toSet() ?: emptySet()).size)
    }
}
