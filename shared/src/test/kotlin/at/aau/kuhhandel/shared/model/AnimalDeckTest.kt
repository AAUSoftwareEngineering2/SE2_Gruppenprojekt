package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnimalDeckTest {
    @Test
    fun test_drawTopCard() {
        val deck =
            AnimalDeck(
                cards =
                    mutableListOf(
                        AnimalCard(id = "1", type = AnimalType.COW),
                    ),
            )

        val card = deck.drawTopCard()

        assertEquals(AnimalType.COW, card?.type)
        assertTrue(deck.isEmpty())
    }
}
