package at.aau.kuhhandel.shared.model

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoneyCardContainerTest {
    private lateinit var container: MoneyCardContainer
    private lateinit var card1: MoneyCard
    private lateinit var card2: MoneyCard
    private lateinit var card3: MoneyCard
    private lateinit var card4: MoneyCard

    @BeforeEach
    fun setUp() {
        container = MoneyCardContainer()
        card1 = MoneyCard("id1", 10)
        card2 = MoneyCard("id2", 20)
        card3 = MoneyCard("id3", 10)
        card4 = MoneyCard("id4", 50)
    }

    @Test
    fun `addCard should add a single card`() {
        container.addCard(card1)
        assertEquals(1, container.size())
        assertTrue(container.getAllCards().contains(card1))
    }

    @Test
    fun `addCards should add multiple cards`() {
        container.addCards(listOf(card1, card2, card3))
        assertEquals(3, container.size())
        assertTrue(container.getAllCards().containsAll(listOf(card1, card2, card3)))
    }

    @Test
    fun `removeCard should remove by exact id and return true`() {
        container.addCard(card1)
        container.addCard(card2)
        assertTrue(container.removeCard(card1))
        assertEquals(1, container.size())
        assertFalse(container.getAllCards().contains(card1))
        assertTrue(container.getAllCards().contains(card2))
    }

    @Test
    fun `removeCard should return false when card with id not present`() {
        container.addCard(card1)
        assertFalse(container.removeCard(card2))
        assertEquals(1, container.size())
    }

    @Test
    fun `removeCardsByValue should remove all cards with given value and return them`() {
        container.addCards(listOf(card1, card2, card3, card4))
        val removed = container.removeCardsByValue(10)
        assertEquals(2, removed.size)
        assertTrue(removed.containsAll(listOf(card1, card3)))
        assertEquals(2, container.size())
        assertTrue(container.getAllCards().containsAll(listOf(card2, card4)))
        assertFalse(container.getAllCards().contains(card1))
    }

    @Test
    fun `removeCardsByValue should return empty list when no cards match`() {
        container.addCards(listOf(card1, card2))
        val removed = container.removeCardsByValue(99)
        assertTrue(removed.isEmpty())
        assertEquals(2, container.size())
    }

    @Test
    fun `getAllCards should return read-only copy`() {
        container.addCards(listOf(card1, card2))
        val cards = container.getAllCards()
        assertEquals(2, cards.size)
        assertTrue(cards.containsAll(listOf(card1, card2)))
        // Die zurückgegebene Liste ist read-only (Kotlin List)
        assertEquals(2, container.size())
    }

    @Test
    fun `totalValue should sum all card values`() {
        assertEquals(0, container.totalValue())
        container.addCard(card1)
        container.addCard(card2)
        container.addCard(card4)
        assertEquals(80, container.totalValue())
        container.removeCard(card2)
        assertEquals(60, container.totalValue())
    }

    @Test
    fun `size should return number of cards`() {
        assertEquals(0, container.size())
        container.addCard(card1)
        assertEquals(1, container.size())
        container.addCards(listOf(card2, card3))
        assertEquals(3, container.size())
        container.removeCard(card1)
        assertEquals(2, container.size())
    }

    @Test
    fun `isEmpty should return true when no cards, false otherwise`() {
        assertTrue(container.isEmpty())
        container.addCard(card1)
        assertFalse(container.isEmpty())
        container.removeCard(card1)
        assertTrue(container.isEmpty())
    }
}
