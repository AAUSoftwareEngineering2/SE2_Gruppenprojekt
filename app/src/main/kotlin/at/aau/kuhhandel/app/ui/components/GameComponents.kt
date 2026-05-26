package at.aau.kuhhandel.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.app.ui.theme.PureWhite
import at.aau.kuhhandel.shared.model.MoneyCard

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
                style = MaterialTheme.typography.displaySmall,
                color = PureWhite,
                fontWeight = FontWeight.Black,
            )
        }

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
            modifier = Modifier.size(width = 90.dp, height = 110.dp),
        )
        // Always show count as requested - styled for high readability
        Text(
            text = count.toString(),
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    shadow =
                        androidx.compose.ui.graphics.Shadow(
                            color = PureWhite,
                            offset =
                                androidx.compose.ui.geometry
                                    .Offset(0f, 0f),
                            blurRadius = 8f,
                        ),
                ),
            color = DarkPurple,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.offset(y = (-5).dp),
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
    Box(
        modifier =
            modifier
                .size(width = 70.dp, height = 100.dp)
                .offset(y = if (isSelected) (-20).dp else 0.dp)
                .then(if (isClickable) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = getMoneyDrawable(card.value)),
            contentDescription = "Money Card ${card.value}",
            modifier = Modifier.size(width = 70.dp, height = 100.dp),
        )

        // Overlay the value text if it's not clearly visible on the asset
        // (Assuming the assets are high-fidelity, but we might want text for accessibility/clarity)
        Text(
            text = card.value.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = DarkPurple,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
        )
    }
}

/** An animated hand of money cards that can fan out. */
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
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (cards.isNotEmpty()) {
            if (!isFanned) {
                // Stack Button (Collapsed State)
                MoneyStackButton(
                    count = cards.size,
                    onClick = onToggleFanned,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
            } else {
                // Grid Layout (Expanded State)
                // We use a Column of Rows to create a readable grid
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
                            .clickable { onToggleFanned() },
                    // Clicking background or cards (if not trade) collapses
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

                    // Simple "Close" hint
                    Text(
                        if (isTradePhase) "Select cards to trade" else "Tap anywhere to close",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkPurple.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}
