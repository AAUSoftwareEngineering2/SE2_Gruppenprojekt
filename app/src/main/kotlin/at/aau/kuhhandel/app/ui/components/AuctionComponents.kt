package at.aau.kuhhandel.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.app.ui.theme.LightPurple
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.PlayerState

/** Displays the current auction details, including the animal card, timer, and current highest bid. */
@Composable
fun AuctionView(
    auction: AuctionState?,
    timerSeconds: Int? = null,
    players: List<PlayerState> = emptyList(),
) {
    if (auction == null) return

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = LightPurple.copy(alpha = 0.9f),
                contentColor = DarkPurple,
            ),
        elevation = CardDefaults.cardElevation(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "LIVE AUCTION",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = DarkPurple,
            )

            timerSeconds?.let {
                Text(
                    "Time left: ${it}s",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Image(
                painter =
                    painterResource(
                        id = getAnimalDrawable(auction.auctionCard.type, AnimalStyle.CARD),
                    ),
                contentDescription = null,
                modifier = Modifier.size(width = 140.dp, height = 200.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                auction.auctionCard.type.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = DarkPurple.copy(alpha = 0.2f),
            )

            val bidderName =
                auction.highestBidderId?.let { id ->
                    players.find { it.id == id }?.name ?: id
                } ?: "No bids yet"

            val bidText =
                if (auction.highestBidderId != null) {
                    "By: $bidderName (Bid: ${auction.highestBid}€)"
                } else {
                    "By: $bidderName"
                }

            Text(
                bidText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Provides buttons for placing incremented bids based on the current highest bid. */
@Composable
fun AuctionControls(
    onBid: (Int) -> Unit,
    currentBid: Int,
    myTotalMoney: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val options = listOf(10, 50, 100)
        options.forEach { amount ->
            val nextBid = currentBid + amount
            val isBluff = nextBid > myTotalMoney
            Button(
                onClick = {
                    if (!isBluff) {
                        onBid(nextBid)
                    }
                },
                enabled = !isBluff,
                colors =
                    if (isBluff) {
                        ButtonDefaults.buttonColors(
                            containerColor = Color.Red,
                            disabledContainerColor = Color.Red,
                            disabledContentColor = Color.White.copy(alpha = 0.6f),
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
            ) {
                Text("+$amount")
            }
        }
    }
}
