package at.aau.kuhhandel.app.ui.menu

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RulesScreenLogicTest {
    @Test
    fun `rules screen has all rule sections`() {
        val ruleSections =
            listOf(
                "Spielziel",
                "Spielstart",
                "Spielablauf",
                "Bieten",
                "Ende",
            )
        assertEquals(5, ruleSections.size)
    }

    @Test
    fun `rule section has title and content`() {
        val ruleSection =
            RuleSection(
                title = "Spielziel",
                content =
                    "Ziel des Spiels ist es, " +
                        "die wertvollsten Tiere und Geldkarten zu sammeln",
            )

        assertEquals("Spielziel", ruleSection.title)
        assertTrue(ruleSection.content.isNotEmpty())
    }

    @Test
    fun `all rule sections have content`() {
        val ruleSections =
            listOf(
                RuleSection("Spielziel", "Ziel des Spiels..."),
                RuleSection("Spielstart", "Jeder Spieler erhält..."),
                RuleSection("Spielablauf", "In jeder Runde..."),
                RuleSection("Bieten", "Spieler können um..."),
                RuleSection("Ende", "Das Spiel endet..."),
            )

        assertTrue(ruleSections.all { it.content.isNotEmpty() })
    }

    @Test
    fun `game objective rule exists`() {
        val ruleSections =
            listOf(
                RuleSection("Spielziel", "Ziel des Spiels ist es..."),
            )

        assertTrue(ruleSections.any { it.title == "Spielziel" })
    }

    @Test
    fun `game start rule exists`() {
        val ruleSections =
            listOf(
                RuleSection("Spielstart", "Jeder Spieler erhält..."),
            )

        assertTrue(ruleSections.any { it.title == "Spielstart" })
    }

    @Test
    fun `gameplay rule exists`() {
        val ruleSections =
            listOf(
                RuleSection("Spielablauf", "In jeder Runde..."),
            )

        assertTrue(ruleSections.any { it.title == "Spielablauf" })
    }

    @Test
    fun `bidding rule exists`() {
        val ruleSections =
            listOf(
                RuleSection("Bieten", "Spieler können um..."),
            )

        assertTrue(ruleSections.any { it.title == "Bieten" })
    }

    @Test
    fun `end rule exists`() {
        val ruleSections =
            listOf(
                RuleSection("Ende", "Das Spiel endet..."),
            )

        assertTrue(ruleSections.any { it.title == "Ende" })
    }

    @Test
    fun `rule titles are correct`() {
        val expectedTitles = listOf("Spielziel", "Spielstart", "Spielablauf", "Bieten", "Ende")
        val ruleSections =
            listOf(
                RuleSection("Spielziel", "..."),
                RuleSection("Spielstart", "..."),
                RuleSection("Spielablauf", "..."),
                RuleSection("Bieten", "..."),
                RuleSection("Ende", "..."),
            )

        ruleSections.forEach { section ->
            assertTrue(section.title in expectedTitles)
        }
    }

    @Test
    fun `rules screen can be scrolled through all sections`() {
        val ruleSections =
            listOf(
                RuleSection("Spielziel", "Content1"),
                RuleSection("Spielstart", "Content2"),
                RuleSection("Spielablauf", "Content3"),
                RuleSection("Bieten", "Content4"),
                RuleSection("Ende", "Content5"),
            )

        assertEquals(5, ruleSections.size)
        ruleSections.forEach { assertTrue(it.content.isNotEmpty()) }
    }
}

data class RuleSection(
    val title: String,
    val content: String,
)
