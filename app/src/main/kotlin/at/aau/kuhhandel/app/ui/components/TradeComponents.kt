package at.aau.kuhhandel.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.shared.model.TradeState

/** Manages the UI for trade challenges, showing offer details and response actions. */
@Composable
fun TradeView(
    trade: TradeState?,
    onAccept: () -> Unit,
    onCounter: () -> Unit,
    modifier: Modifier = Modifier,
    myId: String? = null,
) {
    if (trade == null) return

    val isInitiator = trade.initiatorId == myId
    val isChallenged = trade.targetId == myId

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        modifier = modifier.padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("TRADE CHALLENGE", style = MaterialTheme.typography.titleMedium)
            Text("For: ${trade.requestedAnimalType.name}")

            Spacer(modifier = Modifier.height(8.dp))

            if (isInitiator) {
                if (trade.offeredMoneyCardIds.isEmpty()) {
                    Text("Select cards and send your offer")
                } else {
                    Text("Waiting for response...")
                }
            } else if (isChallenged) {
                Text("Offer received: ${trade.offeredMoneyCardIds.size} cards")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isChallenged) {
                    Button(onClick = onAccept) { Text("Accept") }
                    Button(onClick = onCounter) { Text("Counter") }
                }
            }
        }
    }
}
