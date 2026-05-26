package at.aau.kuhhandel.app.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.ui.theme.AlertRed
import at.aau.kuhhandel.app.ui.theme.AlertRedHighlight
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.app.ui.theme.DefaultPurple
import at.aau.kuhhandel.app.ui.theme.LightPurple
import at.aau.kuhhandel.app.ui.theme.WhitePurple
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

    val infiniteTransition = rememberInfiniteTransition(label = "auctionTimer")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (timerSeconds != null && timerSeconds <= 3) 1.2f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (timerSeconds != null && timerSeconds <= 3) 400 else 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "timerPulse"
    )
    val pulseColor by infiniteTransition.animateColor(
        initialValue = AlertRed,
        targetValue = if (timerSeconds != null && timerSeconds <= 3) AlertRedHighlight else AlertRed.copy(alpha = 0.8f),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (timerSeconds != null && timerSeconds <= 3) 400 else 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "timerColor"
    )

    val bidScale = remember { Animatable(1f) }
    LaunchedEffect(auction.highestBid) {
        if (auction.highestBid > 0) {
            bidScale.animateTo(
                targetValue = 1.15f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
            bidScale.animateTo(1f)
        }
    }

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = LightPurple.copy(alpha = 0.95f),
                contentColor = DarkPurple,
            ),
        elevation = CardDefaults.cardElevation(16.dp),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .height(480.dp) // Fixed height to prevent layout shifts and overlap
            .border(2.dp, DarkPurple.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "LIVE AUCTION",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = DarkPurple.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(8.dp))

            timerSeconds?.let {
                Text(
                    "Time left: ${it}s",
                    style = MaterialTheme.typography.titleLarge,
                    color = pulseColor,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.scale(pulseScale)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(WhitePurple, Color.Transparent),
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            ) {
                Image(
                    painter =
                    painterResource(
                        id = getAnimalDrawable(auction.auctionCard.type, AnimalStyle.CARD),
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(width = 160.dp, height = 220.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                auction.auctionCard.type.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = DarkPurple
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = DarkPurple.copy(alpha = 0.15f),
            )

            val bidderName =
                auction.highestBidderId?.let { id ->
                    players.find { it.id == id }?.name ?: id
                } ?: "No bids yet"

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale(bidScale.value)
            ) {
                Text(
                    if (auction.highestBidderId != null) "Current Bid" else "Starting...",
                    style = MaterialTheme.typography.labelMedium,
                    color = DarkPurple.copy(alpha = 0.6f),
                )

                Text(
                    if (auction.highestBidderId != null) "${auction.highestBid}€" else "Waiting for bids",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (auction.highestBidderId != null) DefaultPurple else DarkPurple,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "By: $bidderName",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkPurple.copy(alpha = 0.8f)
                )
            }
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                colors =
                    if (isBluff) {
                        ButtonDefaults.buttonColors(
                            containerColor = AlertRed,
                            disabledContainerColor = AlertRed.copy(alpha = 0.5f),
                            disabledContentColor = Color.White.copy(alpha = 0.7f),
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = DefaultPurple,
                            contentColor = Color.White
                        )
                    },
            ) {
                Text(
                    "+$amount",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
