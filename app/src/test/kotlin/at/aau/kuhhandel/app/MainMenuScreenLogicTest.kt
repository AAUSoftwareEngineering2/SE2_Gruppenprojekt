package at.aau.kuhhandel.app.ui.menu

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MainMenuScreenLogicTest {
    @Test
    fun `MenuScreenState_Main type is correct`() {
        val state = MenuScreenState.Main
        assertIs<MenuScreenState.Main>(state)
    }

    @Test
    fun `MenuScreenState_RoomCreation type is correct`() {
        val state = MenuScreenState.RoomCreation
        assertIs<MenuScreenState.RoomCreation>(state)
    }

    @Test
    fun `MenuScreenState_RoomJoining type is correct`() {
        val state = MenuScreenState.RoomJoining
        assertIs<MenuScreenState.RoomJoining>(state)
    }

    @Test
    fun `MenuScreenState_Rules type is correct`() {
        val state = MenuScreenState.Rules
        assertIs<MenuScreenState.Rules>(state)
    }

    @Test
    fun `MenuScreenState_Lobby contains correct lobby code`() {
        val lobbyCode = "12345"
        val state = MenuScreenState.Lobby(lobbyCode)
        assertEquals(lobbyCode, state.lobbyCode)
    }

    @Test
    fun `MenuScreenState_Lobby with different codes stores correctly`() {
        val code1 = "11111"
        val code2 = "99999"

        val state1 = MenuScreenState.Lobby(code1)
        val state2 = MenuScreenState.Lobby(code2)

        assertEquals(code1, state1.lobbyCode)
        assertEquals(code2, state2.lobbyCode)
    }

    @Test
    fun `MenuScreenState can transition from Main to RoomCreation`() {
        val mainState = MenuScreenState.Main
        val roomCreationState = MenuScreenState.RoomCreation

        assertIs<MenuScreenState.Main>(mainState)
        assertIs<MenuScreenState.RoomCreation>(roomCreationState)
    }

    @Test
    fun `MenuScreenState can transition from Main to RoomJoining`() {
        val mainState = MenuScreenState.Main
        val roomJoiningState = MenuScreenState.RoomJoining

        assertIs<MenuScreenState.Main>(mainState)
        assertIs<MenuScreenState.RoomJoining>(roomJoiningState)
    }

    @Test
    fun `MenuScreenState can transition to Lobby with code`() {
        val testCode = "54321"
        val lobbyState = MenuScreenState.Lobby(testCode)

        assertIs<MenuScreenState.Lobby>(lobbyState)
        assertEquals(testCode, lobbyState.lobbyCode)
    }
}
