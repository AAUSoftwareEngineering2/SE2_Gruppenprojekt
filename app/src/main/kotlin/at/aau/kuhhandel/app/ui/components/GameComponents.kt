package at.aau.kuhhandel.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.PlayerState
import at.aau.kuhhandel.shared.model.TradeState

@Composable
fun OtherFarm(
    player: PlayerState,
    farmColor: FarmColor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(4.dp).clickable { onClick() },
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
            // Money card count bubble - STRICTLY COUNT ONLY for others
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

@Composable
fun OpponentList(
    players: List<PlayerState>,
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

@Composable
fun DeckView(
    count: String,
    onClick: () -> Unit,
    canClick: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = R.drawable.ig_auctionbox),
                contentDescription = "Deck",
                modifier =
                    Modifier
                        .size(130.dp)
                        .clickable(enabled = canClick) { onClick() },
            )
            Text(
                text = count,
                style = MaterialTheme.typography.displaySmall,
                color = PureWhite,
                fontWeight = FontWeight.Black,
            )
        }

        // The small plus icon from mockup
        Image(
            painter = painterResource(id = R.drawable.ic_plus),
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-20).dp, y = (-20).dp)
                    .size(32.dp),
            alpha = 0.8f,
        )
    }
}

@Composable
fun AuctionView(
    auction: AuctionState?,
    timerSeconds: Int? = null,
) {
    if (auction == null) return

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        elevation = CardDefaults.cardElevation(8.dp),
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("LIVE AUCTION", style = MaterialTheme.typography.labelLarge)

            timerSeconds?.let {
                Text(
                    "Time left: ${it}s",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Image(
                painter = painterResource(id = getAnimalDrawable(auction.auctionCard.type)),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
            )
            Text(auction.auctionCard.type.name, style = MaterialTheme.typography.headlineMedium)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text(
                "Highest Bid: ${auction.highestBid}",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "By: ${auction.highestBidderId ?: "No bids yet"}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun AuctionControls(
    onBid: (Int) -> Unit,
    currentBid: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = { onBid(currentBid + 10) }) { Text("+10") }
        Button(onClick = { onBid(currentBid + 50) }) { Text("+50") }
        Button(onClick = { onBid(currentBid + 100) }) { Text("+100") }
    }
}

@Composable
fun TradeView(
    trade: TradeState?,
    onAccept: () -> Unit,
    onCounter: () -> Unit,
    modifier: Modifier = Modifier,
    myId: String? = null,
) {
    if (trade == null) return

    val isInitiator = trade.initiatingPlayerId == myId
    val isChallenged = trade.challengedPlayerId == myId

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        modifier = modifier.padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("TRADE CHALLENGE", style = MaterialTheme.typography.titleMedium)
            Text("For: ${trade.requestedAnimalType.name}")

            Spacer(modifier = Modifier.height(8.dp))

            if (isInitiator) {
                if (trade.offeredMoneyCardCount == 0) {
                    Text("Select cards and send your offer")
                } else {
                    Text("Waiting for response...")
                }
            } else if (isChallenged) {
                Text("Offer received: ${trade.offeredMoneyCardCount} cards")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isChallenged) {
                    Button(onClick = onAccept) { Text("Accept") }
                    Button(onClick = onCounter) { Text("Counter") }
                }
            }
        }
    }
}

@Composable
fun PlayerFarm(
    player: PlayerState?,
    isMyTurn: Boolean,
    selectedMoneyCardIds: Set<String> = emptySet(),
    onCardClick: (MoneyCard) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Money Hand (Your own cards)
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
                // The asset ig_farm_self contains both the fence (top) and roof (bottom)
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

                // Player Stats Overlay
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

@Composable
fun MoneyHand(
    cards: List<MoneyCard>,
    selectedCardIds: Set<String> = emptySet(),
    onCardClick: (MoneyCard) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        items(cards) { card ->
            MoneyCardView(
                card = card,
                isSelected = selectedCardIds.contains(card.id),
                onClick = { onCardClick(card) },
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
fun MoneyCardView(
    card: MoneyCard,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else WhitePurple,
        shape = MaterialTheme.shapes.small,
        shadowElevation = if (isSelected) 8.dp else 4.dp,
        modifier =
            modifier
                .size(width = 60.dp, height = 90.dp)
                .offset(y = if (isSelected) (-10).dp else 0.dp)
                .clickable { onClick() },
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = card.value.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = DarkPurple,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

fun getAnimalDrawable(type: AnimalType): Int =
    when (type) {
        AnimalType.CHICKEN -> R.drawable.hs_chicken
        AnimalType.GOOSE -> R.drawable.hs_goose
        AnimalType.CAT -> R.drawable.hs_cat
        AnimalType.DOG -> R.drawable.hs_dog
        AnimalType.SHEEP -> R.drawable.hs_sheep
        AnimalType.GOAT -> R.drawable.hs_goat
        AnimalType.DONKEY -> R.drawable.hs_donkey
        AnimalType.PIG -> R.drawable.hs_pig
        AnimalType.COW -> R.drawable.hs_cow
        AnimalType.HORSE -> R.drawable.hs_horse
    }

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
