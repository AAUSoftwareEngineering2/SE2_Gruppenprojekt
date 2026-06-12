package at.aau.kuhhandel.app.ui.menu.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.app.ui.components.MenuBackground
import at.aau.kuhhandel.app.ui.components.MenuCard
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.app.ui.theme.LightPurple
import at.aau.kuhhandel.app.ui.theme.WhitePurple

private data class RuleTopic(
    val id: String,
    val title: String,
    val summary: String,
    val details: List<String>,
)

private val ruleTopics =
    listOf(
        RuleTopic(
            id = "goal",
            title = "What is the goal?",
            summary = "Collect complete animal sets with the highest point value.",
            details =
                listOf(
                    "Players earn animals through auctions and trades.",
                    "Only animal points matter at the end. Money helps during the game, " +
                        "but leftover money is worthless in the final score.",
                    "A complete animal type is safe once a player owns all four cards of that animal.",
                ),
        ),
        RuleTopic(
            id = "setup",
            title = "How is the game set up?",
            summary = "Players start with hidden money; animal cards form the draw pile.",
            details =
                listOf(
                    "Each player starts with two 0 cards and five 10 cards.",
                    "Animal cards are shuffled and drawn one by one for auctions.",
                    "Money should stay hidden, because bluffing and uncertainty are part of the game.",
                ),
        ),
        RuleTopic(
            id = "turn",
            title = "What can I do on my turn?",
            summary = "Choose either an auction or a trade, depending on the game state.",
            details =
                listOf(
                    "At the beginning of the game, only auctions are possible because players do not yet share matching animals.",
                    "Once two players own the same animal type, the active player can offer a trade for that animal.",
                    "When the animal deck is empty, players must trade if a valid trade is available.",
                ),
        ),
        RuleTopic(
            id = "auction",
            title = "How does an auction work?",
            summary =
                "Everyone except the auctioneer bids; the highest bidder wins " +
                    "unless the auctioneer buys back.",
            details =
                listOf(
                    "The auctioneer reveals the top animal card and opens bidding.",
                    "Other players may bid any amount, but each new bid must be higher than the previous one.",
                    "When no one wants to bid higher, the auctioneer closes the auction and the highest bidder receives the animal.",
                    "The highest bidder pays the auctioneer. If nobody bids, the auctioneer may take the animal for free.",
                ),
        ),
        RuleTopic(
            id = "buy_back",
            title = "What is the auctioneer's buy-back right?",
            summary =
                "After the auction closes, the auctioneer may keep the animal " +
                    "by paying the winning bid.",
            details =
                listOf(
                    "The auctioneer cannot bid during the auction.",
                    "Immediately after the final bid, the auctioneer may decide to buy the animal instead.",
                    "If they buy back, they pay the highest bidder the exact winning amount and keep the animal card.",
                ),
        ),
        RuleTopic(
            id = "trade",
            title = "How does a trade work?",
            summary =
                "A player secretly offers money for a matching animal; " +
                    "the other player must counter.",
            details =
                listOf(
                    "A trade is possible when both players own at least one animal of the same type.",
                    "The active player chooses the opponent and animal, then places a hidden money offer.",
                    "The opponent cannot refuse. They place a hidden counter offer.",
                    "Both offers are revealed. The higher offer wins the animal cards; the loser receives the winning money offer.",
                    "0 cards may be included to bluff and must be returned after the trade.",
                ),
        ),
        RuleTopic(
            id = "donkey",
            title = "What happens when a donkey appears?",
            summary = "The donkey adds new money before it is auctioned.",
            details =
                listOf(
                    "Whenever a donkey is revealed, the game pauses before the auction.",
                    "Every player receives new money, including the active player.",
                    "The four donkey payouts are 50, 100, 200, and 500 in order of appearance.",
                    "After the money is distributed, the donkey is auctioned like any other animal.",
                ),
        ),
        RuleTopic(
            id = "payment",
            title = "What if I cannot pay exactly?",
            summary = "There is no change, so overpaying can happen.",
            details =
                listOf(
                    "Money is not exchanged for smaller values.",
                    "If a player cannot pay the exact amount, they must pay more.",
                    "If a bidder cannot pay the amount they bid, they reveal all their money and the auction is repeated.",
                ),
        ),
        RuleTopic(
            id = "end",
            title = "How does the game end?",
            summary = "The game ends once all animal types are complete.",
            details =
                listOf(
                    "When all animals are complete, players add up their animal points.",
                    "Money cards do not count toward the final score.",
                    "The player with the highest animal score wins.",
                ),
        ),
    )

@Composable
fun RulesScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    var expandedTopicId by rememberSaveable { mutableStateOf(ruleTopics.first().id) }

    MenuBackground(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp, bottom = 32.dp, start = 28.dp, end = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            MenuCard(onBack = onBack) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        RulesHeader()
                    }

                    item {
                        RulesAssetStrip()
                    }

                    items(ruleTopics) { topic ->
                        RuleTopicRow(
                            topic = topic,
                            isExpanded = expandedTopicId == topic.id,
                            onClick = {
                                expandedTopicId =
                                    if (expandedTopicId == topic.id) {
                                        ""
                                    } else {
                                        topic.id
                                    }
                            },
                        )
                    }

                    item {
                        TutorialTeaser()
                    }
                }
            }
        }
    }
}

@Composable
private fun RulesHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Kuhhandel Rules",
            style = MaterialTheme.typography.headlineLarge,
            color = DarkPurple,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Auctions, trades, bluffing, and final scoring at a glance.",
            style = MaterialTheme.typography.bodyLarge,
            color = DarkPurple,
        )
    }
}

@Composable
private fun RulesAssetStrip() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(WhitePurple)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RuleAsset(R.drawable.auc_donkey, "donkey card")
        RuleAsset(R.drawable.hs_cow, "animal set")
        RuleAsset(R.drawable.ig_money_revealed_500, "money card")
        RuleAsset(R.drawable.ig_table, "trade table")
    }
}

@Composable
private fun RuleAsset(
    drawableId: Int,
    contentDescription: String,
) {
    Surface(
        modifier = Modifier.size(58.dp),
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Image(
            painter = painterResource(id = drawableId),
            contentDescription = contentDescription,
            modifier = Modifier.padding(8.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun RuleTopicRow(
    topic: RuleTopic,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = DarkPurple,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = topic.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector =
                    if (isExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                contentDescription = null,
                tint = DarkPurple,
                modifier = Modifier.size(32.dp),
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(LightPurple.copy(alpha = 0.35f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                topic.details.forEach { detail ->
                    RuleBullet(text = detail)
                }
            }
        }

        HorizontalDivider(color = LightPurple)
    }
}

@Composable
private fun RuleBullet(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 7.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(DarkPurple),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = DarkPurple,
        )
    }
}

@Composable
private fun TutorialTeaser() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(WhitePurple)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Tutorial",
            style = MaterialTheme.typography.titleLarge,
            color = DarkPurple,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text =
                "A guided walkthrough can live here later, for example as a short " +
                    "slide show for auctions, trades, and scoring.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}
