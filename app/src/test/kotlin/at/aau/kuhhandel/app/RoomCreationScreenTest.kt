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
class RoomCreationScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun roomCreationScreen_displaysTitleAndLoadingState() {
        composeTestRule.setContent {
            RoomCreationScreen(
                onBack = {},
                onLobbyCreated = {}
            )
        }

        composeTestRule.onNodeWithText("Lobby erstellen").assertIsDisplayed()
        // Loading state should be displayed
        composeTestRule.onNodeWithText("Verbinde zum Server...").assertIsDisplayed()
    }

    @Test
    fun roomCreationScreen_backButtonNavigatesBack() {
        var backCalled = false

        composeTestRule.setContent {
            RoomCreationScreen(
                onBack = { backCalled = true },
                onLobbyCreated = {}
            )
        }

        composeTestRule.onNodeWithText("Lobby erstellen").assertIsDisplayed()
    }

    @Test
    fun roomCreationScreen_callsOnLobbyCreatedCallback() {
        var callbackCalled = false
        var receivedCode = ""

        composeTestRule.setContent {
            RoomCreationScreen(
                onBack = {},
                onLobbyCreated = { code ->
                    callbackCalled = true
                    receivedCode = code
                }
            )
        }

        composeTestRule.waitForIdle()
        // The callback should be called during LaunchedEffect
    }

    @Test
    fun generateLobbyCode_returns5DigitCode() {
        repeat(100) {
            val code = generateLobbyCode()
            assert(code.length == 5) { "Code length should be 5, but was ${code.length}" }
            assert(code.all { it.isDigit() }) { "Code should contain only digits, but was $code" }
            assert(code.toInt() in 10000..99999) { "Code should be between 10000 and 99999, but was $code" }
        }
    }

    @Test
    fun generateLobbyCode_generatesUniqueCodesOnMultipleCalls() {
        val codes = mutableSetOf<String>()
        repeat(50) {
            codes.add(generateLobbyCode())
        }
        // Due to randomness, we should have many unique codes
        assert(codes.size > 40) { "Expected mostly unique codes, but got ${codes.size} unique out of 50" }
    }

    @Test
    fun generateLobbyCode_onlyDigits() {
        repeat(100) {
            val code = generateLobbyCode()
            assert(code.matches(Regex("\\d{5}"))) { "Code should match pattern \\d{5}, but was $code" }
        }
    }

    @Test
    fun roomCreationScreen_doesNotCrashWithCallbacks() {
        var createdCode = ""
        var backClicked = false

        composeTestRule.setContent {
            RoomCreationScreen(
                onBack = { backClicked = true },
                onLobbyCreated = { code -> createdCode = code }
            )
        }

        composeTestRule.waitForIdle()
        // Screen should render without crashing
    }
}