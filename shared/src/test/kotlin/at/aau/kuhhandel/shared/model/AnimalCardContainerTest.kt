package at.aau.kuhhandel.shared.model

import at.aau.kuhhandel.shared.enums.AnimalType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimalCardContainerTest {
    private lateinit var container: AnimalCardContainer
    private lateinit var cowCard: AnimalCard
    private lateinit var pigCard: AnimalCard
    private lateinit var sheepCard: AnimalCard
    private lateinit var horseCard: AnimalCard
    private lateinit var chickenCard: AnimalCard

    @BeforeEach
    fun setUp() {
        container = AnimalCardContainer()
        cowCard = AnimalCard("cow1", AnimalType.COW)
        pigCard = AnimalCard("pig1", AnimalType.PIG)
        sheepCard = AnimalCard("sheep1", AnimalType.SHEEP)
        horseCard = AnimalCard("horse1", AnimalType.HORSE)
        chickenCard = AnimalCard("chicken1", AnimalType.CHICKEN)
    }

    @Test
    fun `addCard should add card when less than 4 cards present`() {
        assertTrue(container.addCard(cowCard))
        assertEquals(1, container.numberOfCards())
        assertTrue(container.addCard(pigCard))
        assertEquals(2, container.numberOfCards())
        assertTrue(container.addCard(sheepCard))
        assertEquals(3, container.numberOfCards())
        assertTrue(container.addCard(horseCard))
        assertEquals(4, container.numberOfCards())
    }

    @Test
    fun `addCard should reject card when already 4 cards present`() {
        container.addCard(cowCard)
        container.addCard(pigCard)
        container.addCard(sheepCard)
        container.addCard(horseCard)
        assertFalse(container.addCard(chickenCard))
        assertEquals(4, container.numberOfCards())
    }

    @Test
    fun `removeCard should remove and return card when type exists`() {
        container.addCard(cowCard)
        container.addCard(pigCard)
        val removed = container.removeCard(AnimalType.COW)
        assertNotNull(removed)
        assertEquals(AnimalType.COW, removed.type)
        assertEquals("cow1", removed.id)
        assertEquals(1, container.numberOfCards())
        assertNull(container.removeCard(AnimalType.COW))
    }

    @Test
    fun `removeCard should return null when type not present`() {
        container.addCard(cowCard)
        val removed = container.removeCard(AnimalType.PIG)
        assertNull(removed)
        assertEquals(1, container.numberOfCards())
    }

    @Test
    fun `getAllCards should return read-only copy`() {
        container.addCard(cowCard)
        container.addCard(pigCard)
        val cards = container.getAllCards()
        assertEquals(2, cards.size)
        assertTrue(cards.contains(cowCard))
        assertTrue(cards.contains(pigCard))
        // Die zurückgegebene Liste ist read-only (Kotlin List) und enthält eine Kopie
        assertEquals(2, container.numberOfCards())
    }

    @Test
    fun `getCountByType should return correct count for each type`() {
        assertEquals(0, container.getCountByType(AnimalType.COW))
        container.addCard(cowCard)
        container.addCard(AnimalCard("cow2", AnimalType.COW))
        container.addCard(pigCard)
        assertEquals(2, container.getCountByType(AnimalType.COW))
        assertEquals(1, container.getCountByType(AnimalType.PIG))
        assertEquals(0, container.getCountByType(AnimalType.SHEEP))
    }

    @Test
    fun `getFullQuartetCount should return 0 when less than 4 identical cards`() {
        container.addCard(cowCard)
        container.addCard(AnimalCard("cow2", AnimalType.COW))
        container.addCard(AnimalCard("cow3", AnimalType.COW))
        assertEquals(0, container.getFullQuartetCount())
        container.addCard(pigCard)
        assertEquals(0, container.getFullQuartetCount())
    }

    @Test
    fun `getFullQuartetCount should return 1 when 4 identical cards are present`() {
        repeat(4) { index -> container.addCard(AnimalCard("cow$index", AnimalType.COW)) }
        assertEquals(1, container.getFullQuartetCount())
    }

    @Test
    fun `getFullQuartetCount should handle empty container`() {
        assertEquals(0, container.getFullQuartetCount())
    }

    @Test
    fun `numberOfCards should return current card count`() {
        assertEquals(0, container.numberOfCards())
        container.addCard(cowCard)
        assertEquals(1, container.numberOfCards())
        container.addCard(pigCard)
        assertEquals(2, container.numberOfCards())
        container.removeCard(AnimalType.COW)
        assertEquals(1, container.numberOfCards())
    }

    @Test
    fun `isEmpty should return true when no cards, false otherwise`() {
        assertTrue(container.isEmpty())
        container.addCard(cowCard)
        assertFalse(container.isEmpty())
        container.removeCard(AnimalType.COW)
        assertTrue(container.isEmpty())
    }
}
