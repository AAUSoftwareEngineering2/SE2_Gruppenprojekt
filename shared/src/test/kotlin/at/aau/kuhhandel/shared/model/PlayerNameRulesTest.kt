package at.aau.kuhhandel.shared.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerNameRulesTest {
    @Test
    fun `accepts single letter`() {
        assertNull(PlayerNameRules.validate("A"))
        assertTrue(PlayerNameRules.isValid("A"))
    }

    @Test
    fun `accepts mixed letters and digits up to 8 chars`() {
        assertNull(PlayerNameRules.validate("Felix01"))
        assertNull(PlayerNameRules.validate("ABCDEFGH"))
        assertNull(PlayerNameRules.validate("12345678"))
    }

    @Test
    fun `rejects null name as EMPTY`() {
        assertEquals(PlayerNameRules.Violation.EMPTY, PlayerNameRules.validate(null))
        assertFalse(PlayerNameRules.isValid(null))
    }

    @Test
    fun `rejects empty string as EMPTY`() {
        assertEquals(PlayerNameRules.Violation.EMPTY, PlayerNameRules.validate(""))
    }

    @Test
    fun `rejects names longer than 8 characters as TOO_LONG`() {
        assertEquals(PlayerNameRules.Violation.TOO_LONG, PlayerNameRules.validate("ABCDEFGHI"))
        assertEquals(PlayerNameRules.Violation.TOO_LONG, PlayerNameRules.validate("123456789"))
    }

    @Test
    fun `rejects whitespace as INVALID_CHARACTERS`() {
        assertEquals(
            PlayerNameRules.Violation.INVALID_CHARACTERS,
            PlayerNameRules.validate("Felix 1"),
        )
        assertEquals(
            PlayerNameRules.Violation.INVALID_CHARACTERS,
            PlayerNameRules.validate(" Felix"),
        )
    }

    @Test
    fun `rejects umlauts and special characters as INVALID_CHARACTERS`() {
        assertEquals(
            PlayerNameRules.Violation.INVALID_CHARACTERS,
            PlayerNameRules.validate("Jürgen"),
        )
        assertEquals(
            PlayerNameRules.Violation.INVALID_CHARACTERS,
            PlayerNameRules.validate("Felix!"),
        )
        assertEquals(PlayerNameRules.Violation.INVALID_CHARACTERS, PlayerNameRules.validate("a-b"))
        assertEquals(PlayerNameRules.Violation.INVALID_CHARACTERS, PlayerNameRules.validate("a_b"))
    }
}
