package at.aau.kuhhandel.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.PlayerState

@Composable
fun OtherFarm(
    farmColor: FarmColor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(64.dp),
    ) {
        Image(
            painter = painterResource(id = parseFarmColor(farmColor)),
            contentDescription = "OtherFarm",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun OpponentList(
    players: List<PlayerState>,
    myId: String,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(players.filter { it.id != myId }.indices.toList()) { index ->
            val player = players.filter { it.id != myId }[index]
            val color = FarmColor.entries[index % FarmColor.entries.size]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OtherFarm(farmColor = color, onClick = {})
                Text(player.name, style = MaterialTheme.typography.labelMedium)
                Text("${player.animals.size} Animals", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AuctionView(auction: AuctionState?) {
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
fun PlayerFarm(
    player: PlayerState?,
    isMyTurn: Boolean,
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        border = if (isMyTurn) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ig_farm_self),
                contentDescription = "My Farm",
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(
                    if (isMyTurn) "YOUR TURN" else "YOUR FARM",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isMyTurn) MaterialTheme.colorScheme.primary else Color.Unspecified,
                )
                Text(
                    "${player?.moneyCards?.size ?: 0} Money Cards in hand",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Total: ${player?.totalMoney() ?: 0}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
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
