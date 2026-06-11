package at.aau.kuhhandel.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.app.audio.rememberSoundEffect
import at.aau.kuhhandel.app.ui.theme.AlertRed
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.app.ui.theme.DefaultPurple
import at.aau.kuhhandel.app.ui.theme.LightPurple
import at.aau.kuhhandel.app.ui.theme.PureWhite
import at.aau.kuhhandel.app.ui.theme.WhitePurple
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.Player
import kotlinx.coroutines.delay

private const val DEFAULT_AUCTION_TIMER_MILLIS = 5000L
private const val FULL_CIRCLE_DEGREES = 360f
private const val CLOCK_START_ANGLE_DEGREES = -90f
private const val TIMER_RESET_ANIMATION_MS = 160
private const val TIMER_TEXT_UPDATE_INTERVAL_MS = 100L

@Composable
fun PieTimer(
    timerEndTimeMillis: Long,
    modifier: Modifier = Modifier,
    totalDurationMillis: Long = DEFAULT_AUCTION_TIMER_MILLIS,
    fillColor: Color = DefaultPurple,
    trackColor: Color = LightPurple,
    borderColor: Color = LightPurple,
    showRemainingSeconds: Boolean = true,
) {
    val boundedTotalDurationMillis = totalDurationMillis.coerceAtLeast(1L)
    val progress = remember { Animatable(1f) }
    val displayedRemainingSeconds =
        remember {
            mutableIntStateOf(
                remainingTimerSeconds(
                    timerEndTimeMillis = timerEndTimeMillis,
                    totalDurationMillis = boundedTotalDurationMillis,
                ),
            )
        }

    LaunchedEffect(timerEndTimeMillis, boundedTotalDurationMillis) {
        val targetProgress =
            timerProgress(
                timerEndTimeMillis = timerEndTimeMillis,
                totalDurationMillis = boundedTotalDurationMillis,
            )
        displayedRemainingSeconds.intValue =
            remainingTimerSeconds(
                timerEndTimeMillis = timerEndTimeMillis,
                totalDurationMillis = boundedTotalDurationMillis,
            )

        if (targetProgress > progress.value) {
            progress.animateTo(
                targetValue = targetProgress,
                animationSpec =
                    tween(
                        durationMillis = TIMER_RESET_ANIMATION_MS,
                        easing = LinearEasing,
                    ),
            )
        } else {
            progress.snapTo(targetProgress)
        }

        val countdownMillis =
            remainingTimerMillis(
                timerEndTimeMillis = timerEndTimeMillis,
                totalDurationMillis = boundedTotalDurationMillis,
            )
        if (countdownMillis > 0L) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec =
                    tween(
                        durationMillis = countdownMillis.toInt(),
                        easing = LinearEasing,
                    ),
            )
        } else {
            progress.snapTo(0f)
        }
    }

    LaunchedEffect(timerEndTimeMillis, boundedTotalDurationMillis) {
        while (true) {
            val remainingMillis =
                remainingTimerMillis(
                    timerEndTimeMillis = timerEndTimeMillis,
                    totalDurationMillis = boundedTotalDurationMillis,
                )
            displayedRemainingSeconds.intValue = secondsFromRemainingMillis(remainingMillis)
            if (remainingMillis <= 0L) break
            delay(TIMER_TEXT_UPDATE_INTERVAL_MS)
        }
    }

    Box(
        modifier =
            modifier
                .size(112.dp)
                .semantics {
                    contentDescription = "${displayedRemainingSeconds.intValue} seconds remaining"
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val borderWidth = size.minDimension * 0.055f

            drawCircle(color = trackColor, radius = radius)
            drawArc(
                color = fillColor,
                startAngle = CLOCK_START_ANGLE_DEGREES,
                sweepAngle = FULL_CIRCLE_DEGREES * progress.value,
                useCenter = true,
            )
            drawCircle(
                color = borderColor,
                radius = radius - borderWidth / 2f,
                style = Stroke(width = borderWidth),
            )
        }

        if (showRemainingSeconds) {
            Text(
                text = displayedRemainingSeconds.intValue.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp),
                fontWeight = FontWeight.Black,
                color = PureWhite,
            )
        }
    }
}

private fun remainingTimerMillis(
    timerEndTimeMillis: Long,
    totalDurationMillis: Long,
): Long =
    (timerEndTimeMillis - System.currentTimeMillis())
        .coerceIn(0L, totalDurationMillis)

private fun timerProgress(
    timerEndTimeMillis: Long,
    totalDurationMillis: Long,
): Float =
    remainingTimerMillis(
        timerEndTimeMillis = timerEndTimeMillis,
        totalDurationMillis = totalDurationMillis,
    ).toFloat() / totalDurationMillis

private fun remainingTimerSeconds(
    timerEndTimeMillis: Long,
    totalDurationMillis: Long,
): Int =
    secondsFromRemainingMillis(
        remainingTimerMillis(
            timerEndTimeMillis = timerEndTimeMillis,
            totalDurationMillis = totalDurationMillis,
        ),
    )

private fun secondsFromRemainingMillis(remainingMillis: Long): Int =
    ((remainingMillis + 999L) / 1000L).toInt()

/** Displays the current auction details, including the animal card, timer, and current highest bid. */
@Composable
fun AuctionView(
    modifier: Modifier = Modifier,
    auction: AuctionState?,
    timerSeconds: Int? = null,
    phase: GamePhase = GamePhase.AUCTION_BIDDING,
    players: List<Player> = emptyList(),
    myPlayerId: String? = null,
    footerContent: @Composable () -> Unit = {},
) {
    if (auction == null) return

    val isResultPhase = phase == GamePhase.AUCTION_RESULT
    val titleText = if (isResultPhase) "AUCTION RESULT" else "LIVE AUCTION"

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
                .border(4.dp, DarkPurple.copy(alpha = 0.18f), RoundedCornerShape(42.dp)),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            auction.timerEndTime?.let { timerEndTime ->
                PieTimer(
                    timerEndTimeMillis = timerEndTime,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 20.dp, end = 22.dp),
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    titleText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = DarkPurple.copy(alpha = 0.7f),
                )

                Spacer(modifier = Modifier.height(54.dp))

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

                val highestBidderId = auction.highestBidderId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.scale(bidScale.value),
                ) {
                    Text(
                        if (highestBidderId != null) {
                            "${auction.highestBid}€"
                        } else {
                            "0€"
                        },
                        style =
                            MaterialTheme.typography.displayMedium.copy(
                                fontSize = 86.sp,
                            ),
                        fontWeight = FontWeight.ExtraBold,
                        color =
                            if (highestBidderId != null) {
                                DefaultPurple
                            } else {
                                DarkPurple
                            },
                    )

                    highestBidderId?.let { bidderId ->
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

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = DarkPurple.copy(alpha = 0.12f),
                )

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    footerContent()
                }
            }
        }
    }
}

/** Provides buttons for placing incremented bids based on the current highest bid. */
@Composable
fun AuctionControls(
    modifier: Modifier = Modifier,
    onBid: (Int) -> Unit,
    currentBid: Int,
    isExcluded: Boolean = false,
) {
    val playBidHigherSound = rememberSoundEffect(R.raw.bid_higher)

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
                onClick = {
                    playBidHigherSound()
                    onBid(nextBid)
                },
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
