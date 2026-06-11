package at.aau.kuhhandel.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.app.audio.rememberSoundEffect
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.app.ui.theme.PureWhite
import at.aau.kuhhandel.shared.model.MoneyCard

/** A unified text component for game status messages. */
@Composable
fun GameStatusText(
    text: String,
    alpha: Float = 1f,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style =
            MaterialTheme.typography.headlineSmall.copy(
                shadow =
                    Shadow(
                        color = PureWhite,
                        offset = Offset(4f, 4f),
                        blurRadius = 8f,
                    ),
            ),
        color = DarkPurple.copy(alpha = alpha),
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

/** Shows the draw deck and provides an interaction point to reveal the next card. */
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
                style =
                    MaterialTheme.typography.displaySmall.copy(
                        shadow =
                            Shadow(
                                color = DarkPurple.copy(alpha = 0.8f),
                                offset = Offset(2f, 2f),
                                blurRadius = 4f,
                            ),
                    ),
                color = PureWhite,
                fontWeight = FontWeight.Black,
            )

            if (canClick) {
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
    }
}

/** A button representing a hidden stack of money cards. */
@Composable
fun MoneyStackButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = getHiddenMoneyStackDrawable(count)),
            contentDescription = "Money Stack",
        )
        // Always show count as requested - styled for high readability
        Text(
            text = count.toString(),
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    shadow =
                        Shadow(
                            color = PureWhite,
                            offset = Offset(0f, 0f),
                            blurRadius = 8f,
                        ),
                ),
            color = DarkPurple,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.offset(y = (-5).dp),
        )
    }
}

/** A submitted trade offer shown as a hidden stack on the trading table. */
@Composable
fun TableCards(
    count: Int,
    modifier: Modifier = Modifier,
    isCounterOffer: Boolean = false,
) {
    if (count <= 0) return

    Box(
        modifier = modifier.size(width = 137.dp, height = 77.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter =
                painterResource(
                    id =
                        getHiddenMoneyTableDrawable(
                            count = count,
                            isCounterOffer = isCounterOffer,
                        ),
                ),
            contentDescription =
                if (isCounterOffer) {
                    "Counter offer: $count hidden money cards"
                } else {
                    "Offer: $count hidden money cards"
                },
        )
        Text(
            text = count.toString(),
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    shadow =
                        Shadow(
                            color = PureWhite,
                            offset = Offset.Zero,
                            blurRadius = 8f,
                        ),
                ),
            color = DarkPurple,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** A single money card representation that can be selected. */
@Composable
fun MoneyCardView(
    card: MoneyCard,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isClickable: Boolean = true,
) {
    val playPickMoneyCardSound = rememberSoundEffect(R.raw.pick_money_card)
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "cardScale",
    )

    Card(
        modifier =
            modifier
                .size(width = 70.dp, height = 100.dp)
                .scale(scale)
                .offset(y = if (isSelected) (-20).dp else 0.dp)
                .then(
                    if (isClickable) {
                        Modifier.clickable {
                            playPickMoneyCardSound()
                            onClick()
                        }
                    } else {
                        Modifier
                    },
                ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = getMoneyDrawable(card.value)),
                contentDescription = "Money Card ${card.value}",
                modifier = Modifier.size(width = 70.dp, height = 100.dp),
            )
        }
    }
}

/** An animated hand of money cards that can fan out. */
@Composable
fun o(
    modifier: Modifier = Modifier,
    cards: List<MoneyCard>,
    selectedCardIds: Set<String> = emptySet(),
    onCardClick: (MoneyCard) -> Unit = {},
    isFanned: Boolean,
    onToggleFanned: () -> Unit,
    isTradePhase: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val playFanOutSound = rememberSoundEffect(R.raw.fan_out_card)
    val playFanInSound = rememberSoundEffect(R.raw.fan_in_card)
    var previousIsFanned by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(isFanned) {
        val previous = previousIsFanned
        if (previous != null && previous != isFanned) {
            if (isFanned) {
                playFanOutSound()
            } else {
                playFanInSound()
            }
        }
        previousIsFanned = isFanned
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (isFanned) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {},
                        )
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (cards.isNotEmpty()) {
            AnimatedContent(
                targetState = isFanned,
                transitionSpec = {
                    (
                        scaleIn(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                            initialScale = 0.8f,
                        ) + fadeIn()
                    ).togetherWith(scaleOut() + fadeOut())
                },
                label = "moneyHandFanning",
            ) { targetIsFanned ->
                if (!targetIsFanned) {
                    // Stack Button (Collapsed State)
                    MoneyStackButton(
                        count = cards.size,
                        onClick = onToggleFanned,
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                } else {
                    // Grid Layout (Expanded State)
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val cardsPerRow = 4
                        cards.chunked(cardsPerRow).forEach { rowCards ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                rowCards.forEach { card ->
                                    MoneyCardView(
                                        card = card,
                                        isSelected = selectedCardIds.contains(card.id),
                                        onClick = { onCardClick(card) },
                                        isClickable = isTradePhase,
                                    )
                                }
                            }
                        }

                        if (!isTradePhase) {
                            Text(
                                text = "Tap outside to close",
                                style = MaterialTheme.typography.labelSmall,
                                color = DarkPurple.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoneyHand(
    modifier: Modifier = Modifier,
    cards: List<MoneyCard>,
    selectedCardIds: Set<String> = emptySet(),
    onCardClick: (MoneyCard) -> Unit = {},
    isFanned: Boolean,
    onToggleFanned: () -> Unit,
    isTradePhase: Boolean = false,
) {
    o(
        modifier = modifier,
        cards = cards,
        selectedCardIds = selectedCardIds,
        onCardClick = onCardClick,
        isFanned = isFanned,
        onToggleFanned = onToggleFanned,
        isTradePhase = isTradePhase,
    )
}

@Preview(showBackground = true)
@Composable
fun MoneyHandPreview() {
    val sampleCards =
        listOf(
            MoneyCard("1", 0),
            MoneyCard("2", 10),
            MoneyCard("3", 50),
            MoneyCard("4", 100),
            MoneyCard("5", 0),
        )
    Box(modifier = Modifier.height(300.dp)) {
        MoneyHand(
            cards = sampleCards,
            isFanned = true,
            onToggleFanned = {},
            selectedCardIds = setOf("3"),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TableCardsPreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TableCards(count = 4)
        TableCards(
            count = 12,
            isCounterOffer = true,
        )
    }
}
