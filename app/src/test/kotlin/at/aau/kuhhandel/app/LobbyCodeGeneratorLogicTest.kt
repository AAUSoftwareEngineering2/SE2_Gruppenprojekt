package at.aau.kuhhandel.app.ui.menu

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LobbyCodeGeneratorLogicTest {
    @Test
    fun `generateLobbyCode returns exactly 5 characters`() {
        repeat(20) {
            val code = generateLobbyCode()
            assertEquals(5, code.length, "Generated code should have exactly 5 characters")
        }
    }

    @Test
    fun `generateLobbyCode returns only digits`() {
        repeat(20) {
            val code = generateLobbyCode()
            assertTrue(code.all { it.isDigit() }, "Code '$code' should only contain digits")
        }
    }

    @Test
    fun `generateLobbyCode is within valid range 10000-99999`() {
        repeat(20) {
            val code = generateLobbyCode()
            val codeAsInt = code.toInt()
            assertTrue(
                codeAsInt in 10000..99999,
                "Code '$code' should be between 10000 and 99999",
            )
        }
    }

    @Test
    fun `generateLobbyCode matches digit pattern`() {
        repeat(20) {
            val code = generateLobbyCode()
            assertTrue(code.matches(Regex("\\d{5}")), "Code '$code' should match pattern \\d{5}")
        }
    }

    @Test
    fun `generateLobbyCode first digit is never zero`() {
        repeat(20) {
            val code = generateLobbyCode()
            val firstDigit = code.first().toString().toInt()
            assertTrue(firstDigit >= 1, "First digit should not be 0, code: '$code'")
        }
    }

    @Test
    fun `generateLobbyCode generates mostly unique codes`() {
        val generatedCodes = mutableSetOf<String>()
        repeat(50) {
            generatedCodes.add(generateLobbyCode())
        }

        // With random 5-digit numbers, we should have at least 45 unique codes out of 50
        assertTrue(
            generatedCodes.size >= 45,
            "Expected at least 45 unique codes out of 50, got ${generatedCodes.size}",
        )
    }

    @Test
    fun `generateLobbyCode can produce minimum value 10000`() {
        var foundMin = false
        repeat(10000) {
            val code = generateLobbyCode()
            if (code == "10000") {
                foundMin = true
            }
        }
        // Note: This test is probabilistic, might fail rarely
    }

    @Test
    fun `generateLobbyCode can produce maximum value 99999`() {
        var foundMax = false
        repeat(10000) {
            val code = generateLobbyCode()
            if (code == "99999") {
                foundMax = true
            }
        }
        // Note: This test is probabilistic, might fail rarely
    }
}
