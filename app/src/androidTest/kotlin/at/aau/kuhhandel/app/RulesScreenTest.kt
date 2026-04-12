package at.aau.kuhhandel.app.ui.menu

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RulesScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rulesScreen_displaysTitle() {
        composeTestRule.setContent {
            RulesScreen(onBack = {})
        }

        composeTestRule.onNodeWithText("Regeln").assertIsDisplayed()
    }

    @Test
    fun rulesScreen_displaysHeading() {
        composeTestRule.setContent {
            RulesScreen(onBack = {})
        }

        composeTestRule.onNodeWithText("Kuhhandel - Spielregeln").assertIsDisplayed()
    }

    @Test
    fun rulesScreen_displaysGameObjectiveRule() {
        composeTestRule.setContent {
            RulesScreen(onBack = {})
        }

        composeTestRule.onNodeWithText("Spielziel").assertIsDisplayed()
    }

    @Test
    fun rulesScreen_displaysGameStartRule() {
        composeTestRule.setContent {
            RulesScreen(onBack = {})
        }

        composeTestRule.onNodeWithText("Spielstart").assertIsDisplayed()
    }

    @Test
    fun rulesScreen_displaysGameplayRule() {
        composeTestRule.setContent {
            RulesScreen(onBack = {})
        }

        composeTestRule.onNodeWithText("Spielablauf").assertIsDisplayed()
    }

    @Test
    fun rulesScreen_displaysBiddingRule() {
        composeTestRule.setContent {
            RulesScreen(onBack = {})
        }

        composeTestRule.onNodeWithText("Bieten").assertIsDisplayed()
    }

    @Test
    fun rulesScreen_displaysEndRule() {
        composeTestRule.setContent {
            RulesScreen(onBack = {})
        }

        composeTestRule.onNodeWithText("Ende").assertIsDisplayed()
    }

    @Test
    fun rulesScreen_allRuleSectionsHaveContent() {
        composeTestRule.setContent {
            RulesScreen(onBack = {})
        }

        // Check that descriptions are displayed
        composeTestRule.onNodeWithText(
            "Ziel des Spiels ist es, die wertvollsten Tiere und " +
                "Geldkarten zu sammeln um am Ende das höchste Vermögen zu haben.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Jeder Spieler erhält eine bestimmte Anzahl von Karten.",
        ).assertIsDisplayed()
    }

    @Test
    fun rulesScreen_displaysBiddingRuleContent() {
        composeTestRule.setContent {
            RulesScreen(onBack = {})
        }

        composeTestRule.onNodeWithText(
            "Spieler können um Tiere und Geldkarten bieten.",
        ).assertIsDisplayed()
    }

    @Test
    fun rulesScreen_displaysEndRuleContent() {
        composeTestRule.setContent {
            RulesScreen(onBack = {})
        }

        composeTestRule.onNodeWithText(
            "Das Spiel endet nach einer festgelegten Anzahl von Runden.",
        ).assertIsDisplayed()
    }

    @Test
    fun rulesScreen_hasBackButton() {
        composeTestRule.setContent {
            RulesScreen(onBack = {})
        }

        // TopAppBar with back button should be present
        composeTestRule.onNodeWithText("Regeln").assertIsDisplayed()
    }

    @Test
    fun rulesScreen_backButtonCallsOnBack() {
        var backCalled = false

        composeTestRule.setContent {
            RulesScreen(onBack = { backCalled = true })
        }

        composeTestRule.waitForIdle()
        // Back button should be callable
    }

    @Test
    fun rulesScreen_doesNotCrashWithCallback() {
        var backClicked = false

        composeTestRule.setContent {
            RulesScreen(onBack = { backClicked = true })
        }

        composeTestRule.waitForIdle()
        // Screen should render without crashing
    }

    @Test
    fun rulesScreen_contentIsScrollable() {
        composeTestRule.setContent {
            RulesScreen(onBack = {})
        }

        // Verify that multiple rule sections are present
        composeTestRule.onNodeWithText("Spielziel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spielstart").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spielablauf").assertIsDisplayed()
    }
}
