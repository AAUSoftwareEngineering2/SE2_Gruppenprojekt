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
import androidx.compose.ui.draw.scale
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
    val results = uiState.results
    val winner = results.firstOrNull()
    val runnerUps = results.drop(1)

    MenuBackground(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                MenuCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // 1. WINNER HEADER
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 24.dp),
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
                            modifier = Modifier.padding(vertical = 4.dp),
                        )

                        // Points Badge
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = DefaultPurple,
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

                    // 2. SPACER FOR ANIMALS (Large Padding above/below hero row)
                    Spacer(modifier = Modifier.height(280.dp))

                    // 3. RUNNER-UP GRID
                    FlowRow(
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    ) {
                        runnerUps.forEachIndexed { index: Int, result: PlayerResult ->
                            Box(modifier = Modifier.fillMaxWidth(0.47f)) {
                                RunnerUpCard(rank = index + 2, result = result)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // 4. HOME BUTTON (Inside the Card)
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

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 5. HERO ANIMALS OVERLAY (Cat - Horse - Donkey - Cow - Dog)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .offset(y = (-110).dp), // Moves animals UP to avoid ranking overlap
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // 1. Dog (Right Pop-out) - BACK
                    Image(
                        painter = painterResource(id = R.drawable.ig_dog),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(130.dp)
                                .offset(x = 30.dp),
                    )

                    // 2. Cat (Left Pop-out)
                    Image(
                        painter = painterResource(id = R.drawable.ig_cat),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .size(120.dp)
                                .offset(x = (-35).dp, y = 5.dp),
                    )

                    // 3. Horse (Center-Left)
                    Image(
                        painter = painterResource(id = R.drawable.ig_horse),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .size(200.dp)
                                .offset(x = (-75).dp), // Moved right from -95
                    )

                    // 4. Cow (Center-Right)
                    Image(
                        painter = painterResource(id = R.drawable.ig_cow),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .size(180.dp)
                                .offset(x = 75.dp),
                    )

                    // 5. Donkey (Middle) - FRONT
                    Image(
                        painter = painterResource(id = R.drawable.ig_donkey),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .size(180.dp)
                                .scale(scaleX = -1f, scaleY = 1f)
                                .offset(y = (-5).dp),
                    )
                }
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
                "Player Name",
                animals = generateQuartets(listOf(AnimalType.HORSE, AnimalType.COW)),
            ),
            Player("2", "Player Two", animals = generateQuartets(listOf(AnimalType.PIG))),
            Player("3", "Player Three", animals = generateQuartets(listOf(AnimalType.DOG))),
            Player("4", "Player Four", animals = generateQuartets(listOf(AnimalType.CAT))),
            Player("5", "Player Five", animals = generateQuartets(listOf(AnimalType.GOOSE))),
        )
    val results = ScoreCalculator.getLeaderboard(samplePlayers)
    val uiState = GameUiState(results = results)

    AndroidAppTheme {
        WinScreen(uiState = uiState, onHome = {})
    }
}

private fun generateQuartets(types: List<AnimalType>): List<AnimalCard> =
    types.flatMap { type ->
        (1..4).map { AnimalCard("${type.name}-$it", type) }
    }
