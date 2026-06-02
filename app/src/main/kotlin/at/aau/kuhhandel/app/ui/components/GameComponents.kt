package at.aau.kuhhandel.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.app.audio.rememberSoundEffect
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.app.ui.theme.PureWhite
import at.aau.kuhhandel.app.ui.theme.WhitePurple
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

/** A horizontal list of the player's money cards. */
@Composable
fun MoneyHand(
    modifier: Modifier = Modifier,
    cards: List<MoneyCard>,
    selectedCardIds: Set<String> = emptySet(),
    onCardClick: (MoneyCard) -> Unit = {},
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

/** A single money card representation that can be selected. */
@Composable
fun MoneyCardView(
    card: MoneyCard,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playPickMoneyCardSound = rememberSoundEffect(R.raw.pick_money_card)

    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else WhitePurple,
        shape = MaterialTheme.shapes.small,
        shadowElevation = if (isSelected) 8.dp else 4.dp,
        modifier =
            modifier
                .size(width = 60.dp, height = 90.dp)
                .offset(y = if (isSelected) (-10).dp else 0.dp)
                .clickable {
                    playPickMoneyCardSound()
                    onClick()
                },
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
