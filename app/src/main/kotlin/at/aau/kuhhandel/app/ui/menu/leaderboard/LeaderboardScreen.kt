package at.aau.kuhhandel.app.ui.menu.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.ui.components.MenuBackground
import at.aau.kuhhandel.app.ui.components.MenuCard
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.shared.model.GlobalLeaderboardItem

@Composable
fun LeaderboardScreen(
    uiState: LeaderboardUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    MenuBackground {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp, bottom = 32.dp, start = 32.dp, end = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            MenuCard(onBack = onBack) {
                when {
                    uiState.errorMessage != null -> {
                        Text(
                            text = uiState.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onRefresh,
                            modifier = Modifier.fillMaxWidth(0.6f),
                        ) {
                            Text("Retry")
                        }
                    }

                    uiState.isLoading -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Loading Leaderboard...")
                    }

                    uiState.items.isEmpty() -> {
                        Text(
                            text = "Leaderboard",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "No scores recorded in the last 7 days.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Leaderboard",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh leaderboard",
                                    tint = DarkPurple,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        LeaderboardList(items = uiState.items)
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderboardList(items: List<GlobalLeaderboardItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
            ) {
                Text("Rank", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.25f))
                Text("Player", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.45f))
                Text(
                    text = "Score",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.3f),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
        }

        items(items) { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "#${item.rank}",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(0.25f),
                    )
                    Text(item.playerName, modifier = Modifier.weight(0.45f), maxLines = 1)
                    Text(
                        text = "${item.score}",
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.3f),
                    )
                }
            }
        }
    }
}
