package at.aau.kuhhandel.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.ui.components.AuctionControls
import at.aau.kuhhandel.app.ui.components.AuctionView
import at.aau.kuhhandel.app.ui.components.DeckView
import at.aau.kuhhandel.app.ui.components.MoneyHand
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.app.ui.theme.PureWhite
import at.aau.kuhhandel.shared.enums.GamePhase

@Composable
fun ChoicePhaseContent(
    uiState: GameUiState,
    onRevealCard: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DeckView(
            count = uiState.deckCountText,
            onClick = onRevealCard,
            canClick = uiState.isMyTurn,
        )
        val statusMessage =
            if (uiState.isMyTurn) {
                "YOUR TURN !"
            } else {
                "Waiting for ${uiState.activePlayerName}..."
            }
        GameStatusText(
            text = statusMessage,
            modifier = Modifier.padding(top = 16.dp),
            color = DarkPurple,
        )
    }
}

@Composable
fun AuctionPhaseContent(
    uiState: GameUiState,
    onPlaceBid: (Int) -> Unit,
    onBuyBack: (Boolean) -> Unit,
    onToggleMoneyCard: (String) -> Unit,
    onSubmitAuctionPayment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val auctionState = uiState.auctionState

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
        ) {
            AuctionView(
                auction = auctionState,
                timerSeconds = uiState.auctionTimerSeconds,
                phase = uiState.currentPhase,
                myPlayerId = uiState.myPlayerId,
                playerName = uiState::playerName,
                footerContent = {
                    val phase = uiState.currentPhase
                    if (!uiState.isAuctioneer &&
                        (phase == GamePhase.AUCTION_BIDDING)
                    ) {
                        AuctionControls(
                            onBid = onPlaceBid,
                            currentBid = auctionState?.highestBid ?: 0,
                            isExcluded =
                                auctionState?.excludedPlayerIds?.contains(
                                    uiState.myPlayerId,
                                ) == true,
                        )
                    } else if (phase == GamePhase.AUCTION_PAYMENT) {
                        val buyerName =
                            auctionState?.buyerId?.let { buyerId ->
                                uiState.playerName(buyerId)
                            } ?: "the buyer"
                        GameStatusText(
                            text =
                                if (uiState.isAuctionBuyer) {
                                    "Pay ${uiState.auctionBidToPay}€ — select your money cards"
                                } else {
                                    "Waiting for $buyerName to pay..."
                                },
                            color = DarkPurple,
                        )
                    } else if (phase == GamePhase.AUCTIONEER_DECISION ||
                        phase == GamePhase.AUCTION_RESULT
                    ) {
                        val highestBidderId = auctionState?.highestBidderId
                        val buyerId = auctionState?.buyerId
                        val auctioneerId = auctionState?.auctioneerId
                        val buyerName =
                            if (buyerId != null) {
                                uiState.playerName(buyerId)
                            } else {
                                ""
                            }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (phase == GamePhase.AUCTION_RESULT) {
                                val resultText =
                                    when {
                                        highestBidderId == null ->
                                            "$buyerName got the animal for free!"
                                        buyerId == auctioneerId -> "$buyerName bought back!"
                                        else -> "$buyerName won the auction!"
                                    }
                                GameStatusText(
                                    text = resultText,
                                    color = DarkPurple,
                                )
                            } else if (uiState.isAuctioneer) {
                                val statusText =
                                    if (highestBidderId == null) {
                                        "Auction Closed. No one bid!"
                                    } else {
                                        "Auction Closed. Choose your action:"
                                    }
                                GameStatusText(
                                    text = statusText,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    color = DarkPurple,
                                )
                                if (highestBidderId == null) {
                                    Button(onClick = { onBuyBack(true) }) {
                                        Text("CONTINUE")
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onBuyBack(true) },
                                            enabled = uiState.canAuctioneerBuyBack,
                                        ) {
                                            Text("Buy Back")
                                        }
                                        Button(onClick = { onBuyBack(false) }) {
                                            Text("Let Winner Buy")
                                        }
                                    }
                                }
                            } else {
                                GameStatusText(
                                    text = "Waiting for player ${uiState.activePlayerName}...",
                                    color = DarkPurple,
                                )
                            }
                        }
                    }
                },
            )
        }

        if (uiState.isAuctionBuyer) {
            AuctionPaymentSelection(
                uiState = uiState,
                onToggleMoneyCard = onToggleMoneyCard,
                onSubmit = onSubmitAuctionPayment,
            )
        }
    }
}

@Composable
private fun BoxScope.AuctionPaymentSelection(
    uiState: GameUiState,
    onToggleMoneyCard: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MoneyHand(
            cards = uiState.myMoneyCards,
            selectedCardIds = uiState.selectedMoneyCardIds,
            onCardClick = { onToggleMoneyCard(it.id) },
            isFanned = true,
            onToggleFanned = {},
            isTradePhase = true,
        )
        Button(
            onClick = onSubmit,
            enabled = uiState.canSubmitAuctionPayment && !uiState.isTradeActionSubmitting,
        ) {
            Text("Pay (${uiState.selectedMoneyTotal}€ / ${uiState.auctionBidToPay}€)")
        }
    }
}

@Composable
fun GameStatusText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PureWhite,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = color,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
