package at.aau.kuhhandel.app.ui.menu

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerDataClassTest {
    @Test
    fun `Player stores name correctly`() {
        val playerName = "TestPlayer"
        val player = Player(playerName, isHost = false, isReady = false)
        assertEquals(playerName, player.name)
    }

    @Test
    fun `Player stores isHost as true`() {
        val player = Player("Host", isHost = true, isReady = false)
        assertTrue(player.isHost)
    }

    @Test
    fun `Player stores isHost as false`() {
        val player = Player("Guest", isHost = false, isReady = false)
        assertFalse(player.isHost)
    }

    @Test
    fun `Player stores isReady as true`() {
        val player = Player("Ready", isHost = false, isReady = true)
        assertTrue(player.isReady)
    }

    @Test
    fun `Player stores isReady as false`() {
        val player = Player("NotReady", isHost = false, isReady = false)
        assertFalse(player.isReady)
    }

    @Test
    fun `Player with all properties true`() {
        val player = Player("HostReady", isHost = true, isReady = true)
        assertEquals("HostReady", player.name)
        assertTrue(player.isHost)
        assertTrue(player.isReady)
    }

    @Test
    fun `Player with all properties false`() {
        val player = Player("GuestNotReady", isHost = false, isReady = false)
        assertEquals("GuestNotReady", player.name)
        assertFalse(player.isHost)
        assertFalse(player.isReady)
    }

    @Test
    fun `Player with special characters in name`() {
        val specialName = "Spieler_1-Test@2024"
        val player = Player(specialName, isHost = true, isReady = true)
        assertEquals(specialName, player.name)
    }

    @Test
    fun `Player with empty name`() {
        val player = Player("", isHost = false, isReady = false)
        assertEquals("", player.name)
    }

    @Test
    fun `Player with long name`() {
        val longName = "VeryLongPlayerNameThatShouldStillWork"
        val player = Player(longName, isHost = false, isReady = false)
        assertEquals(longName, player.name)
    }

    @Test
    fun `Player copy creates new instance with same values`() {
        val originalPlayer = Player("Original", isHost = true, isReady = true)
        val copiedPlayer = originalPlayer.copy()

        assertEquals(originalPlayer.name, copiedPlayer.name)
        assertEquals(originalPlayer.isHost, copiedPlayer.isHost)
        assertEquals(originalPlayer.isReady, copiedPlayer.isReady)
    }

    @Test
    fun `Player copy with name change`() {
        val originalPlayer = Player("Original", isHost = true, isReady = true)
        val modifiedPlayer = originalPlayer.copy(name = "Modified")

        assertEquals("Original", originalPlayer.name)
        assertEquals("Modified", modifiedPlayer.name)
        assertEquals(originalPlayer.isHost, modifiedPlayer.isHost)
    }

    @Test
    fun `Player copy with isHost change`() {
        val originalPlayer = Player("Player", isHost = true, isReady = true)
        val modifiedPlayer = originalPlayer.copy(isHost = false)

        assertTrue(originalPlayer.isHost)
        assertFalse(modifiedPlayer.isHost)
    }

    @Test
    fun `Player copy with isReady change`() {
        val originalPlayer = Player("Player", isHost = false, isReady = true)
        val modifiedPlayer = originalPlayer.copy(isReady = false)

        assertTrue(originalPlayer.isReady)
        assertFalse(modifiedPlayer.isReady)
    }

    @Test
    fun `Multiple Player instances are independent`() {
        val player1 = Player("Player1", isHost = true, isReady = true)
        val player2 = Player("Player2", isHost = false, isReady = false)

        assertEquals("Player1", player1.name)
        assertEquals("Player2", player2.name)
        assertTrue(player1.isHost)
        assertFalse(player2.isHost)
    }

    @Test
    fun `Player equals when all properties match`() {
        val player1 = Player("SameName", isHost = true, isReady = true)
        val player2 = Player("SameName", isHost = true, isReady = true)

        assertEquals(player1, player2)
    }

    @Test
    fun `Player not equals when name differs`() {
        val player1 = Player("Name1", isHost = true, isReady = true)
        val player2 = Player("Name2", isHost = true, isReady = true)

        assertTrue(player1 != player2)
    }
}
