package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnimalDeckTest {
    @Test
    fun test_drawTopCard_multiple() {
        val deck =
            AnimalDeck(
                cards =
                    listOf(
                        AnimalCard(id = "1", type = AnimalType.COW),
                        AnimalCard(id = "2", type = AnimalType.PIG),
                    ),
            )

        val (card1, deck1) = deck.drawTopCard()
        assertEquals("2", card1?.id) // AnimalDeck.drawTopCard uses last()
        assertEquals(1, deck1.size())

        val (card2, deck2) = deck1.drawTopCard()
        assertEquals("1", card2?.id)
        assertEquals(0, deck2.size())
    }

    @Test
    fun test_drawTopCard_empty() {
        val deck = AnimalDeck(cards = emptyList())
        val (card, updatedDeck) = deck.drawTopCard()
        kotlin.test.assertNull(card)
        assertTrue(updatedDeck.isEmpty())
    }
}
