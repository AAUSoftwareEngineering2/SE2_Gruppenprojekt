package at.aau.kuhhandel.app.ui.menu

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomJoiningValidationTest {
    @Test
    fun `valid 5 digit code passes validation`() {
        val code = "12345"
        assertTrue(isValidLobbyCode(code))
    }

    @Test
    fun `code with only digits is valid`() {
        val code = "99999"
        assertTrue(isValidLobbyCode(code))
    }

    @Test
    fun `code with less than 5 digits is invalid`() {
        val code = "1234"
        assertFalse(isValidLobbyCode(code))
    }

    @Test
    fun `code with more than 5 digits is invalid`() {
        val code = "123456"
        assertFalse(isValidLobbyCode(code))
    }

    @Test
    fun `empty code is invalid`() {
        val code = ""
        assertFalse(isValidLobbyCode(code))
    }

    @Test
    fun `code with letters is invalid`() {
        val code = "1234A"
        assertFalse(isValidLobbyCode(code))
    }

    @Test
    fun `code with special characters is invalid`() {
        val code = "12@45"
        assertFalse(isValidLobbyCode(code))
    }

    @Test
    fun `code with spaces is invalid`() {
        val code = "123 45"
        assertFalse(isValidLobbyCode(code))
    }

    @Test
    fun `minimum valid code 10000 is valid`() {
        assertTrue(isValidLobbyCode("10000"))
    }

    @Test
    fun `maximum valid code 99999 is valid`() {
        assertTrue(isValidLobbyCode("99999"))
    }

    @Test
    fun `code 00000 is invalid (starts with zero)`() {
        assertFalse(isValidLobbyCode("00000"))
    }

    @Test
    fun `code with leading zero is invalid`() {
        assertFalse(isValidLobbyCode("01234"))
    }
}

fun isValidLobbyCode(code: String): Boolean =
    code.length == 5 &&
        code.all {
            it.isDigit()
        } &&
        code[0] != '0'
