package at.aau.kuhhandel.app.ui.menu

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomCreationLogicTest {
    @Test
    fun `room creation generates valid lobby code`() {
        val code = generateLobbyCode()
        assertEquals(5, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun `room creation code is in valid range`() {
        val code = generateLobbyCode()
        val codeInt = code.toInt()
        assertTrue(codeInt >= 10000 && codeInt <= 99999)
    }

    @Test
    fun `multiple room creations generate different codes`() {
        val codes = mutableSetOf<String>()
        repeat(10) {
            codes.add(generateLobbyCode())
        }
        assertTrue(codes.size >= 8, "Expected mostly unique codes, got ${codes.size} out of 10")
    }

    @Test
    fun `room creation code format is correct`() {
        repeat(10) {
            val code = generateLobbyCode()
            assertTrue(code.matches(Regex("\\d{5}")))
        }
    }

    @Test
    fun `first digit of room code is never zero`() {
        repeat(10) {
            val code = generateLobbyCode()
            assertTrue(code[0].isDigit() && code[0] != '0')
        }
    }

    @Test
    fun `generated code can be converted to integer`() {
        val code = generateLobbyCode()
        val codeInt = code.toInt()
        assertEquals(code, codeInt.toString())
    }

    @Test
    fun `room code has consistent format`() {
        repeat(5) {
            val code = generateLobbyCode()
            assertEquals(5, code.length)
            assertTrue(code.all { c -> c in '0'..'9' })
        }
    }
}
