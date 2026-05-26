package at.aau.kuhhandel.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.app.ui.theme.PureWhite
import at.aau.kuhhandel.app.ui.theme.WhitePurple
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.Player

/** Displays a summary of an opponent's farm, including their name and money card count. */
@Composable
fun OtherFarm(
    player: Player,
    farmColor: FarmColor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .padding(4.dp)
                .clickable { onClick() },
    ) {
        Text(
            text = player.name,
            style = MaterialTheme.typography.titleMedium,
            color = DarkPurple.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
        )
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = parseFarmColor(farmColor)),
                contentDescription = "OtherFarm",
                modifier = Modifier.size(135.dp),
            )
            Surface(
                color = PureWhite,
                shape = MaterialTheme.shapes.medium,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-5).dp, y = (-5).dp),
                border = BorderStroke(1.dp, DarkPurple.copy(alpha = 0.2f)),
                shadowElevation = 2.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ig_money_hidden_diagonal_1),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = player.moneyCards.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = DarkPurple,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

/** Renders a grid of all opponents in the game. */
@Composable
fun OpponentList(
    players: List<Player>,
    myId: String?,
    onOpponentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val opponents = players.filter { it.id != myId }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        opponents.chunked(2).forEachIndexed { rowIndex, rowPlayers ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                rowPlayers.forEachIndexed { colIndex, player ->
                    val index = (rowIndex * 2) + colIndex
                    val color = FarmColor.entries[index % FarmColor.entries.size]
                    OtherFarm(
                        player = player,
                        farmColor = color,
                        onClick = { onOpponentClick(player.id) },
                    )
                }
            }
        }
    }
}

/** Displays the current player's farm area, including their hand of money cards and turn status. */
@Composable
fun PlayerFarm(
    modifier: Modifier = Modifier,
    player: Player?,
    isMyTurn: Boolean,
    selectedMoneyCardIds: Set<String> = emptySet(),
    onCardClick: (MoneyCard) -> Unit = {},
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (player != null) {
                MoneyHand(
                    cards = player.moneyCards,
                    selectedCardIds = selectedMoneyCardIds,
                    onCardClick = onCardClick,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ig_farm_self),
                    contentDescription = "My Farm Area",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.Center,
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp, start = 24.dp, end = 24.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = player?.name ?: "YOU",
                            style = MaterialTheme.typography.headlineMedium,
                            color = WhitePurple,
                            fontWeight = FontWeight.Black,
                        )
                        if (isMyTurn) {
                            Text(
                                "YOUR TURN",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFFFEB3B),
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }

                    Surface(
                        color = DarkPurple.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            "${player?.moneyCards?.size ?: 0} Cards | Total: ${player?.totalMoney() ?: 0}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = WhitePurple,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/** Maps a [FarmColor] to its corresponding farm drawable resource ID. */
fun parseFarmColor(color: FarmColor): Int =
    when (color) {
        FarmColor.BLUE -> R.drawable.ig_farm_blue
        FarmColor.YELLOW -> R.drawable.ig_farm_yellow
        FarmColor.RED -> R.drawable.ig_farm_red
        FarmColor.ORANGE -> R.drawable.ig_farm_orange
        FarmColor.PURPLE -> R.drawable.ig_farm_purple
        FarmColor.WHITE -> R.drawable.ig_farm_white
    }

enum class FarmColor {
    BLUE,
    YELLOW,
    RED,
    ORANGE,
    PURPLE,
    WHITE,
}
