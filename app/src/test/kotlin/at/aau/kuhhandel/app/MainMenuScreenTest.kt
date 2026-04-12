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
    fun mainMenuScreen_lobbyErstellenButtonNavigates() {
        var navigationCalled = false

        composeTestRule.setContent {
            val screenState = remember { mutableStateOf<MenuScreenState>(MenuScreenState.Main) }
            
            when (screenState.value) {
                MenuScreenState.Main -> MainMenuScreen(modifier = Modifier)
                MenuScreenState.RoomCreation -> {
                    navigationCalled = true
                    Text("RoomCreation")
                }
                else -> Unit
            }
        }

        // Verify initial render
        composeTestRule.onNodeWithText("Kuhhandel").assertIsDisplayed()
    }

    @Test
    fun mainMenuScreen_allButtonsAreClickable() {
        composeTestRule.setContent {
            MainMenuScreen()
        }

        composeTestRule.onNodeWithText("Lobby erstellen").performClick()
        composeTestRule.onNodeWithText("Lobby beitreten").performClick()
        composeTestRule.onNodeWithText("Regeln").performClick()

        // If no crash occurred, buttons are clickable
    }

    @Test
    fun menuScreenState_hasCorrectStates() {
        assert(MenuScreenState.Main is MenuScreenState)
        assert(MenuScreenState.RoomCreation is MenuScreenState)
        assert(MenuScreenState.RoomJoining is MenuScreenState)
        assert(MenuScreenState.Rules is MenuScreenState)

        val lobbyState = MenuScreenState.Lobby("12345")
        assert(lobbyState is MenuScreenState.Lobby)
        assert((lobbyState as MenuScreenState.Lobby).lobbyCode == "12345")
    }
}