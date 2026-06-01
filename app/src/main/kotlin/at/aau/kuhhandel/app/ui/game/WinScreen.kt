package at.aau.kuhhandel.app.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.app.ui.components.MenuBackground
import at.aau.kuhhandel.app.ui.components.MenuCard
import at.aau.kuhhandel.app.ui.theme.AndroidAppTheme
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.app.ui.theme.DefaultPurple
import at.aau.kuhhandel.app.ui.theme.LightPurple
import at.aau.kuhhandel.app.ui.theme.PureWhite
import at.aau.kuhhandel.app.ui.theme.Typography
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.Player
import at.aau.kuhhandel.shared.utils.PlayerResult
import at.aau.kuhhandel.shared.utils.ScoreCalculator

@Composable
fun WinScreen(
    uiState: GameUiState,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val leaderboard = uiState.leaderboard
    val winner = leaderboard.firstOrNull()
    val runnerUps = leaderboard.drop(1) // Adaptable for any number of remaining players

    MenuBackground(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 40.dp)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            MenuCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Winner Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(
                        text = "WINNER",
                        style =
                            Typography.headlineLarge.copy(
                                color = DefaultPurple,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "👑", fontSize = 28.sp)
                }

                if (winner != null) {
                    Text(
                        text = winner.playerName.uppercase(),
                        style =
                            Typography.headlineLarge.copy(
                                color = DarkPurple,
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        textAlign = TextAlign.Center,
                    )

                    // Points Badge
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = DefaultPurple,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(
                            text = "${winner.points}p",
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                            color = PureWhite,
                            style =
                                Typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Animal Illustration Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    val animals =
                        listOf(
                            R.drawable.auc_cat to 100.dp,
                            R.drawable.auc_horse to 150.dp,
                            R.drawable.auc_pig to 110.dp,
                            R.drawable.auc_dog to 125.dp,
                        )
                    animals.forEachIndexed { index, pair ->
                        val (res, size) = pair
                        Image(
                            painter = painterResource(id = res),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(size)
                                    .offset(x = ((-25) * index).dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Runner-up Grid
                FlowRow(
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    runnerUps.forEachIndexed { index, result ->
                        Box(modifier = Modifier.fillMaxWidth(0.47f)) {
                            RunnerUpCard(rank = index + 2, result = result)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Home Button
            Button(
                onClick = onHome,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                shape = RoundedCornerShape(20.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color.Gray,
                        contentColor = PureWhite,
                    ),
            ) {
                Text(
                    text = "HOME",
                    style =
                        Typography.headlineLarge.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }
        }
    }
}

@Composable
fun RunnerUpCard(
    rank: Int,
    result: PlayerResult,
) {
    val suffix =
        when (rank) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DefaultPurple,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$rank$suffix",
                    color = LightPurple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = result.playerName,
                    color = PureWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Text(
                text = "${result.points}p",
                color = LightPurple,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun WinScreenPreview() {
    val samplePlayers =
        listOf(
            Player(
                "1",
                "Winner Name",
                animals = generateQuartets(listOf(AnimalType.HORSE, AnimalType.COW)),
            ),
            Player("2", "Player Two", animals = generateQuartets(listOf(AnimalType.PIG))),
            Player("3", "Player Three", animals = generateQuartets(listOf(AnimalType.DOG))),
        )
    val leaderboard = ScoreCalculator.getLeaderboard(samplePlayers)
    val uiState = GameUiState(leaderboard = leaderboard)

    AndroidAppTheme {
        WinScreen(uiState = uiState, onHome = {})
    }
}

private fun generateQuartets(types: List<AnimalType>): List<AnimalCard> =
    types.flatMap { type ->
        (1..4).map { AnimalCard("${type.name}-$it", type) }
    }
