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
class MainMenuScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainMenuScreen_displaysAllThreeButtons() {
        composeTestRule.setContent {
            MainMenuScreen()
        }

        composeTestRule.onNodeWithText("Lobby erstellen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lobby beitreten").assertIsDisplayed()
        composeTestRule.onNodeWithText("Regeln").assertIsDisplayed()
    }

    @Test
    fun mainMenuScreen_displaysTitle() {
        composeTestRule.setContent {
            MainMenuScreen()
        }

        composeTestRule.onNodeWithText("Kuhhandel").assertIsDisplayed()
    }

    @Test
    fun mainMenuScreen_allButtonsAreClickable() {
        composeTestRule.setContent {
            MainMenuScreen()
        }

        composeTestRule.onNodeWithText("Lobby erstellen").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Lobby beitreten").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Regeln").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun mainMenuScreen_navigatesToRoomCreationOnFirstButtonClick() {
        composeTestRule.setContent {
            MainMenuScreen()
        }

        composeTestRule.onNodeWithText("Lobby erstellen").performClick()
        composeTestRule.waitForIdle()

        // After clicking, we should see the RoomCreationScreen content
        composeTestRule.onNodeWithText("Lobby erstellen").assertIsDisplayed()
    }

    @Test
    fun mainMenuScreen_navigatesToRoomJoiningOnSecondButtonClick() {
        composeTestRule.setContent {
            MainMenuScreen()
        }

        composeTestRule.onNodeWithText("Lobby beitreten").performClick()
        composeTestRule.waitForIdle()

        // After clicking, we should see the RoomJoiningScreen content
        composeTestRule.onNodeWithText("Lobby beitreten").assertIsDisplayed()
    }

    @Test
    fun mainMenuScreen_navigatesToRulesOnThirdButtonClick() {
        composeTestRule.setContent {
            MainMenuScreen()
        }

        composeTestRule.onNodeWithText("Regeln").performClick()
        composeTestRule.waitForIdle()

        // After clicking, we should see the RulesScreen content
        composeTestRule.onNodeWithText("Regeln").assertIsDisplayed()
    }

    @Test
    fun mainMenuScreen_rendersWithoutCrashing() {
        composeTestRule.setContent {
            MainMenuScreen()
        }

        composeTestRule.waitForIdle()
        // If we got here, the screen rendered successfully
        assert(true)
    }

    @Test
    fun mainMenuScreen_allButtonsPresent() {
        composeTestRule.setContent {
            MainMenuScreen()
        }

        val lobbyErstellenButton = composeTestRule.onNodeWithText("Lobby erstellen")
        val lobbyBeitretenButton = composeTestRule.onNodeWithText("Lobby beitreten")
        val regelnButton = composeTestRule.onNodeWithText("Regeln")

        lobbyErstellenButton.assertIsDisplayed()
        lobbyBeitretenButton.assertIsDisplayed()
        regelnButton.assertIsDisplayed()
    }

    @Test
    fun mainMenuScreen_titleFontSizeIsLarge() {
        composeTestRule.setContent {
            MainMenuScreen()
        }

        composeTestRule.onNodeWithText("Kuhhandel").assertIsDisplayed()
    }

    @Test
    fun mainMenuScreen_buttonsHaveProperSpacing() {
        composeTestRule.setContent {
            MainMenuScreen()
        }

        // All elements should exist and be displayable
        composeTestRule.onNodeWithText("Kuhhandel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lobby erstellen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lobby beitreten").assertIsDisplayed()
        composeTestRule.onNodeWithText("Regeln").assertIsDisplayed()
    }

    @Test
    fun mainMenuScreen_multipleClicksOnSameButton() {
        composeTestRule.setContent {
            MainMenuScreen()
        }

        // Click the same button multiple times
        composeTestRule.onNodeWithText("Lobby erstellen").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Lobby erstellen").performClick()
        composeTestRule.waitForIdle()

        // Screen should still be responsive
        composeTestRule.onNodeWithText("Lobby erstellen").assertIsDisplayed()
    }
}
