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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.aau.kuhhandel.app.R
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
    myPlayerId: String? = null,
    modifier: Modifier = Modifier,
    footerContent: @Composable () -> Unit = {},
) {
    if (auction == null) return

    // Timer animation logic: Faster and brighter pulse when time is running out (< 3s)
    val isTimeLow = (timerSeconds != null && timerSeconds <= 3)
    val pulseDuration = if (isTimeLow) 400 else 1000

    val infiniteTransition = rememberInfiniteTransition(label = "auctionTimer")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isTimeLow) 1.2f else 1.05f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = pulseDuration, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "timerPulse",
    )
    val pulseColor by infiniteTransition.animateColor(
        initialValue = AlertRed,
        targetValue = if (isTimeLow) AlertRedHighlight else AlertRed.copy(alpha = 0.8f),
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = pulseDuration),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "timerColor",
    )

    // "Pop" animation whenever a new bid is placed
    val bidScale = remember { Animatable(1f) }
    LaunchedEffect(auction.highestBid) {
        if (auction.highestBid > 0) {
            bidScale.animateTo(
                targetValue = 1.15f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
            )
            bidScale.animateTo(1f)
        }
    }

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = WhitePurple.copy(alpha = 0.86f),
                contentColor = DarkPurple,
            ),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(42.dp),
        modifier =
            modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .height(700.dp)
                .border(4.dp, DarkPurple.copy(alpha = 0.18f), RoundedCornerShape(42.dp)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "LIVE AUCTION",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = DarkPurple.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(6.dp))

            timerSeconds?.let {
                Text(
                    "Time left: ${it}s",
                    style = MaterialTheme.typography.headlineMedium,
                    color = pulseColor,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.scale(pulseScale),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier.size(width = 330.dp, height = 340.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // The Auction Box rendered behind the animal
                Image(
                    painter = painterResource(id = R.drawable.ig_auctionbox),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(220.dp)
                            .offset(y = 74.dp),
                )

                // The Animal Card rendered in front of the box
                Image(
                    painter =
                        painterResource(
                            id = getAnimalDrawable(auction.auctionCard.type, AnimalStyle.CARD),
                        ),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(width = 220.dp, height = 280.dp)
                            .offset(x = (-24).dp, y = (-36).dp),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (auction.highestBidderId == null && timerSeconds == null) {
                val auctioneerName =
                    players.find { it.id == auction.auctioneerId }?.name ?: "The auctioneer"
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Auction Closed",
                        style = MaterialTheme.typography.labelMedium,
                        color = DarkPurple.copy(alpha = 0.6f),
                    )
                    Text(
                        "$auctioneerName got the\n${auction.auctionCard.type.name} for free!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = DefaultPurple,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.scale(bidScale.value),
                ) {
                    Text(
                        if (auction.highestBidderId !=
                            null
                        ) {
                            "${auction.highestBid}€"
                        } else {
                            "0€"
                        },
                        style =
                            MaterialTheme.typography.displayMedium.copy(
                                fontSize = 86.sp,
                            ),
                        fontWeight = FontWeight.ExtraBold,
                        color = if (auction.highestBidderId != null) DefaultPurple else DarkPurple,
                    )

                    auction.highestBidderId?.let { bidderId ->
                        val bidderName =
                            if (bidderId == myPlayerId) {
                                "You"
                            } else {
                                players.find { it.id == bidderId }?.name ?: bidderId
                            }
                        Text(
                            "from $bidderName",
                            style = MaterialTheme.typography.labelMedium,
                            color = DarkPurple.copy(alpha = 0.62f),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = DarkPurple.copy(alpha = 0.12f),
            )

            footerContent()
        }
    }
}

/** Provides buttons for placing incremented bids based on the current highest bid. */
@Composable
fun AuctionControls(
    onBid: (Int) -> Unit,
    currentBid: Int,
    isExcluded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (isExcluded) {
        Column(
            modifier = modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "EXCLUDED",
                color = AlertRed,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "You bluffed and cannot bid anymore.",
                color = DarkPurple.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        return
    }

    Row(
        modifier = modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        val options = listOf(10, 50, 100)
        options.forEach { amount ->
            val nextBid = currentBid + amount
            Button(
                onClick = { onBid(nextBid) },
                shape = RoundedCornerShape(24.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                contentPadding = PaddingValues(0.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = LightPurple.copy(alpha = 0.74f),
                        contentColor = DarkPurple,
                    ),
            ) {
                Box(
                    modifier = Modifier.size(width = 94.dp, height = 70.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_plus),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 10.dp, y = 8.dp)
                                .size(34.dp),
                    )
                    Text(
                        amount.toString(),
                        fontWeight = FontWeight.Black,
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 38.sp,
                            ),
                    )
                }
            }
        }
    }
}
