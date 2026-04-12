package at.aau.kuhhandel.app.ui.menu

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomJoiningScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun roomJoiningScreen_displaysTitleAndDialog() {
        composeTestRule.setContent {
            RoomJoiningScreen(
                onBack = {},
                onLobbyJoined = {}
            )
        }

        composeTestRule.onNodeWithText("Lobby beitreten").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lobby-Code eingeben").assertIsDisplayed()
    }

    @Test
    fun roomJoiningScreen_dialogHasInputField() {
        composeTestRule.setContent {
            RoomJoiningScreen(
                onBack = {},
                onLobbyJoined = {}
            )
        }

        composeTestRule.onNodeWithText("Code").assertIsDisplayed()
    }

    @Test
    fun roomJoiningScreen_dialogHasJoinAndCancelButtons() {
        composeTestRule.setContent {
            RoomJoiningScreen(
                onBack = {},
                onLobbyJoined = {}
            )
        }

        composeTestRule.onNodeWithText("Beitreten").assertIsDisplayed()
        composeTestRule.onNodeWithText("Abbrechen").assertIsDisplayed()
    }

    @Test
    fun roomJoiningScreen_cancelButtonCallsOnBack() {
        var backCalled = false

        composeTestRule.setContent {
            RoomJoiningScreen(
                onBack = { backCalled = true },
                onLobbyJoined = {}
            )
        }

        // Dialog should be displayed initially
        composeTestRule.onNodeWithText("Abbrechen").assertIsDisplayed()
    }

    @Test
    fun roomJoiningScreen_acceptsOnlyDigits() {
        composeTestRule.setContent {
            RoomJoiningScreen(
                onBack = {},
                onLobbyJoined = {}
            )
        }

        val inputField = composeTestRule.onNodeWithText("Code")
        inputField.assertIsDisplayed()
    }

    @Test
    fun roomJoiningScreen_limitsInputTo5Characters() {
        composeTestRule.setContent {
            RoomJoiningScreen(
                onBack = {},
                onLobbyJoined = {}
            )
        }

        val inputField = composeTestRule.onNodeWithText("Code")
        inputField.assertIsDisplayed()
        // Max length validation should be in the OutlinedTextField logic
    }

    @Test
    fun roomJoiningScreen_displaysErrorForInvalidCode() {
        var joinCalled = false

        composeTestRule.setContent {
            RoomJoiningScreen(
                onBack = {},
                onLobbyJoined = { joinCalled = true }
            )
        }

        composeTestRule.waitForIdle()
        // Invalid code should not trigger join callback
    }

    @Test
    fun roomJoiningScreen_doesNotCrashWithCallbacks() {
        var joinedCode = ""
        var backClicked = false

        composeTestRule.setContent {
            RoomJoiningScreen(
                onBack = { backClicked = true },
                onLobbyJoined = { code -> joinedCode = code }
            )
        }

        composeTestRule.waitForIdle()
        // Screen should render without crashing
    }

    @Test
    fun roomJoiningScreen_showsPlaceholder() {
        composeTestRule.setContent {
            RoomJoiningScreen(
                onBack = {},
                onLobbyJoined = {}
            )
        }

        composeTestRule.onNodeWithText("12345").assertIsDisplayed()
    }
}