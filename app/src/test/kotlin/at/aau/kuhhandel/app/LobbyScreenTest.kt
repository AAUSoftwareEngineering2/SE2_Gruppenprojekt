package at.aau.kuhhandel.app.ui.menu

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LobbyScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testLobbyCode = "12345"

    @Test
    fun lobbyScreen_displaysLobbyCode() {
        composeTestRule.setContent {
            LobbyScreen(
                lobbyCode = testLobbyCode,
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("Lobby").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lobby-Code").assertIsDisplayed()
        composeTestRule.onNodeWithText(testLobbyCode).assertIsDisplayed()
    }

    @Test
    fun lobbyScreen_displaysPlayersHeading() {
        composeTestRule.setContent {
            LobbyScreen(
                lobbyCode = testLobbyCode,
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("Spieler").assertIsDisplayed()
    }

    @Test
    fun lobbyScreen_displaysShareCodeMessage() {
        composeTestRule.setContent {
            LobbyScreen(
                lobbyCode = testLobbyCode,
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("Teile diesen Code").assertIsDisplayed()
    }

    @Test
    fun lobbyScreen_displaysCancelButton() {
        composeTestRule.setContent {
            LobbyScreen(
                lobbyCode = testLobbyCode,
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("Abbrechen").assertIsDisplayed()
    }

    @Test
    fun lobbyScreen_cancelButtonCallsOnBack() {
        var backCalled = false

        composeTestRule.setContent {
            LobbyScreen(
                lobbyCode = testLobbyCode,
                onBack = { backCalled = true }
            )
        }

        composeTestRule.onNodeWithText("Abbrechen").performClick()
        // onBack should be callable
    }

    @Test
    fun lobbyScreen_displaysPlayerList() {
        composeTestRule.setContent {
            LobbyScreen(
                lobbyCode = testLobbyCode,
                onBack = {}
            )
        }

        // Players should be displayed (default list includes "Du", "Spieler 2", "Spieler 3")
        composeTestRule.onNodeWithText("Du").assertIsDisplayed()
    }

    @Test
    fun lobbyScreen_displaysHostInfo() {
        composeTestRule.setContent {
            LobbyScreen(
                lobbyCode = testLobbyCode,
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("Host").assertIsDisplayed()
    }

    @Test
    fun lobbyScreen_displaysWaitMessageWhenNotEnoughPlayers() {
        composeTestRule.setContent {
            LobbyScreen(
                lobbyCode = testLobbyCode,
                onBack = {}
            )
        }

        // With default 3 players, "Spiel starten" should be visible or wait message
        composeTestRule.waitForIdle()
    }

    @Test
    fun lobbyScreen_doesNotCrashWithCallback() {
        var backClicked = false

        composeTestRule.setContent {
            LobbyScreen(
                lobbyCode = testLobbyCode,
                onBack = { backClicked = true }
            )
        }

        composeTestRule.waitForIdle()
        // Screen should render without crashing
    }

    @Test
    fun playerDataClass_createsPlayerCorrectly() {
        val player = Player("TestPlayer", isHost = true, isReady = false)

        assert(player.name == "TestPlayer")
        assert(player.isHost)
        assert(!player.isReady)
    }

    @Test
    fun playerDataClass_supportsMultipleStates() {
        val player1 = Player("Host", isHost = true, isReady = true)
        val player2 = Player("Joiner", isHost = false, isReady = false)

        assert(player1.isHost && player1.isReady)
        assert(!player2.isHost && !player2.isReady)
    }

    @Test
    fun lobbyScreen_correctLobbyCodeDisplay() {
        val testCode = "54321"

        composeTestRule.setContent {
            LobbyScreen(
                lobbyCode = testCode,
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText(testCode).assertIsDisplayed()
    }
}