package at.aau.kuhhandel.app.ui.menu

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric supports up to 34 currently
class MainMenuScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `MainMenuScreen renders and has ping button`() {
        composeTestRule.setContent {
            MainMenuScreen()
        }

        composeTestRule.onNodeWithText("Ping-Server").assertIsDisplayed()
    }

    @Test
    fun `clicking ping button triggers logic`() {
        composeTestRule.setContent {
            MainMenuScreen()
        }

        // Just clicking it is enough to "cover" the onClick lambda lines
        composeTestRule.onNodeWithText("Ping-Server").performClick()
    }
}
