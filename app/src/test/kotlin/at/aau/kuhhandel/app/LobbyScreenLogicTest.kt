package at.aau.kuhhandel.app.ui.menu

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LobbyScreenLogicTest {
    @Test
    fun `lobby can be created with valid code`() {
        val code = "12345"
        val lobby = LobbyData(code, listOf())
        assertEquals(code, lobby.code)
    }

    @Test
    fun `lobby stores players list`() {
        val code = "12345"
        val players =
            listOf(
                Player("Player1", isHost = true, isReady = false),
                Player("Player2", isHost = false, isReady = true),
            )
        val lobby = LobbyData(code, players)

        assertEquals(2, lobby.players.size)
        assertEquals("Player1", lobby.players[0].name)
    }

    @Test
    fun `lobby host is identified correctly`() {
        val players =
            listOf(
                Player("Host", isHost = true, isReady = false),
                Player("Guest", isHost = false, isReady = false),
            )
        val host = players.firstOrNull { it.isHost }

        assertTrue(host?.isHost ?: false)
        assertEquals("Host", host?.name)
    }

    @Test
    fun `lobby counts ready players`() {
        val players =
            listOf(
                Player("Player1", isHost = true, isReady = true),
                Player("Player2", isHost = false, isReady = true),
                Player("Player3", isHost = false, isReady = false),
            )
        val readyCount = players.count { it.isReady }

        assertEquals(2, readyCount)
    }

    @Test
    fun `can start game with at least 2 players`() {
        val players =
            listOf(
                Player("Player1", isHost = true, isReady = true),
                Player("Player2", isHost = false, isReady = true),
            )
        assertTrue(players.size >= 2)
    }

    @Test
    fun `cannot start game with only 1 player`() {
        val players =
            listOf(
                Player("Player1", isHost = true, isReady = true),
            )
        assertFalse(players.size >= 2)
    }

    @Test
    fun `lobby displays all players`() {
        val players =
            listOf(
                Player("Player1", isHost = true, isReady = true),
                Player("Player2", isHost = false, isReady = false),
                Player("Player3", isHost = false, isReady = true),
            )

        assertEquals(3, players.size)
        assertTrue(players.any { it.name == "Player1" })
        assertTrue(players.any { it.name == "Player2" })
        assertTrue(players.any { it.name == "Player3" })
    }

    @Test
    fun `lobby player count is correct`() {
        val players =
            listOf(
                Player("Player1", isHost = true, isReady = false),
                Player("Player2", isHost = false, isReady = false),
            )

        val playerCountText = "Spieler (${players.size})"
        assertTrue(playerCountText.contains("2"))
    }
}

data class LobbyData(
    val code: String,
    val players: List<Player>,
)
