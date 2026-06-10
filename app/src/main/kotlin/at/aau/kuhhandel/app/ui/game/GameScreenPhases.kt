package at.aau.kuhhandel.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
    onSubmitAuctionPayment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gameState = uiState.gameState
    val auctionState = gameState?.auctionState

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(top = 32.dp),
    ) {
        AuctionView(
            auction = auctionState,
            timerSeconds = uiState.auctionTimerSeconds,
            phase = gameState?.phase ?: GamePhase.AUCTION_BIDDING,
            players = gameState?.players ?: emptyList(),
            myPlayerId = uiState.myPlayerId,
            footerContent = {
                val phase = gameState?.phase ?: GamePhase.AUCTION_BIDDING
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
                } else if (phase == GamePhase.AUCTIONEER_DECISION ||
                    phase == GamePhase.AUCTION_PAYMENT ||
                    phase == GamePhase.AUCTION_RESULT
                ) {
                    val highestBidderId = auctionState?.highestBidderId
                    val buyerId = auctionState?.buyerId
                    val auctioneerId = auctionState?.auctioneerId
                    val buyerName =
                        if (buyerId != null) {
                            gameState.players.find { it.id == buyerId }?.name ?: "Unknown"
                        } else {
                            ""
                        }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (phase == GamePhase.AUCTION_RESULT) {
                            val resultText =
                                when {
                                    highestBidderId == null -> "$buyerName got the animal for free!"
                                    buyerId == auctioneerId -> "$buyerName bought back!"
                                    else -> "$buyerName won the auction!"
                                }
                            GameStatusText(
                                text = resultText,
                                color = DarkPurple,
                            )
                        } else if (phase == GamePhase.AUCTION_PAYMENT) {
                            if (uiState.isAuctionPayer) {
                                GameStatusText(
                                    text = "Select money cards to pay ${auctionState?.highestBid}.",
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    color = DarkPurple,
                                )
                                Button(
                                    onClick = onSubmitAuctionPayment,
                                    enabled = uiState.canSubmitAuctionPayment,
                                ) {
                                    Text("Pay (${uiState.selectedMoneyTotal})")
                                }
                            } else {
                                val payerName =
                                    gameState
                                        ?.players
                                        ?.find { it.id == highestBidderId }
                                        ?.name ?: "winner"
                                GameStatusText(
                                    text = "Waiting for $payerName to pay...",
                                    color = DarkPurple,
                                )
                            }
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
                                if (uiState.canAuctioneerAffordBuyBack) {
                                    Text(
                                        text =
                                            "Select money cards totaling at least " +
                                                "${auctionState.highestBid} to buy back.",
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                } else {
                                    Text(
                                        text = "You do not have enough money to buy back.",
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onBuyBack(true) },
                                        enabled = uiState.canSubmitAuctionPayment,
                                    ) {
                                        Text("Buy Back (${uiState.selectedMoneyTotal})")
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
