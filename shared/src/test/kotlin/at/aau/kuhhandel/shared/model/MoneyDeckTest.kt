package at.aau.kuhhandel.shared.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MoneyDeckTest {
    @Test
    fun test_moneyDeck_hasCorrectTotalSize() {
        val deck = MoneyDeck()

        assertEquals(55, deck.cards.size)
    }

    @Test
    fun test_moneyDeck_distribution() {
        val deck = MoneyDeck()

        val values = deck.cards.map { it.value }

        assertEquals(10, values.count { it == 0 })
        assertEquals(25, values.count { it == 10 })
        assertEquals(5, values.count { it == 50 })
        assertEquals(5, values.count { it == 100 })
        assertEquals(5, values.count { it == 200 })
        assertEquals(5, values.count { it == 500 })
    }
}
