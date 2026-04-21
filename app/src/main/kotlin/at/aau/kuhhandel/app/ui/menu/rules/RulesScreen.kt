package at.aau.kuhhandel.app.ui.menu.rules

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.ui.components.MenuBackground
import at.aau.kuhhandel.app.ui.components.MenuCard

@Composable
fun RulesScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    MenuBackground(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp, bottom = 32.dp, start = 32.dp, end = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            MenuCard(onBack = onBack) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "Kuhhandel - Game Rules",
                        style = MaterialTheme.typography.headlineMedium,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    RuleSection(
                        title = "Goal of the Game",
                        content =
                            "The goal of the game is to collect the most valuable animals " +
                                "and money cards to have the highest fortune at the end.",
                    )

                    RuleSection(
                        title = "Game Start",
                        content =
                            "Each player receives a certain number of cards. " +
                                "The remaining cards form the draw pile.",
                    )

                    RuleSection(
                        title = "Gameplay",
                        content =
                            "In each round, a player can perform an action: " +
                                "trade cards, bid, or pass.",
                    )

                    RuleSection(
                        title = "Bidding",
                        content =
                            "Players can bid for animals and money cards. " +
                                "The highest bid wins the card.",
                    )

                    RuleSection(
                        title = "Game End",
                        content =
                            "The game ends after a set number of rounds. " +
                                "The player with the highest fortune wins.",
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "Full Rules",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleSection(
    title: String,
    content: String,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            content,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}
