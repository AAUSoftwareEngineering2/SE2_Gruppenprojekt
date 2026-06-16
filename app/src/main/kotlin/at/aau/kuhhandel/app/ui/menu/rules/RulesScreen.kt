package at.aau.kuhhandel.app.ui.menu.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.app.ui.components.MenuBackground
import at.aau.kuhhandel.app.ui.components.MenuCard
import at.aau.kuhhandel.app.ui.components.MoneyCardView
import at.aau.kuhhandel.app.ui.theme.DarkPurple
import at.aau.kuhhandel.app.ui.theme.LightPurple
import at.aau.kuhhandel.app.ui.theme.WhitePurple
import at.aau.kuhhandel.shared.model.MoneyCard

private const val FIRST_GAME_TIP_COUNT = 3

private data class RuleTopic(
    val id: String,
    val title: String,
    val summary: String,
    val details: List<String>,
)

private data class RuleGroup(
    val title: String,
    val topics: List<RuleTopic>,
)

private data class AnimalValue(
    val name: String,
    val points: Int,
    val drawableId: Int,
)

private data class TutorialSlide(
    val title: String,
    val body: String,
    val action: TutorialAction,
    val drawableIds: List<Int>,
)

private enum class TutorialAction {
    ANIMAL_VALUES,
    BID_HIGHER,
    BUY_BACK,
    SELECT_TRADE,
    SEND_OFFER,
    TRADE_RESPONSE,
    WIN_SCREEN,
    SPY_SELECT_FARM,
    SPY_ARM_EYE,
    SPY_SHAKE,
    SPY_REVEAL,
    SPY_CATCH,
}

private val ruleGroups =
    listOf(
        RuleGroup(
            title = "Basics",
            topics =
                listOf(
                    RuleTopic(
                        id = "goal",
                        title = "What is the goal?",
                        summary = "Collect complete animal sets with the highest point value.",
                        details =
                            listOf(
                                "Players earn animals through auctions and trades.",
                                "Only animal points matter at the end. Money helps during " +
                                    "the game, but leftover money is worthless in the final score.",
                                "A complete animal type is safe once a player owns all four " +
                                    "cards of that animal.",
                            ),
                    ),
                    RuleTopic(
                        id = "setup",
                        title = "How is the game set up?",
                        summary =
                            "Players start with hidden money; animal cards form the draw pile.",
                        details =
                            listOf(
                                "Each player starts with two 0 cards and five 10 cards.",
                                "Animal cards are shuffled and drawn one by one for auctions.",
                                "Money should stay hidden, because bluffing and uncertainty " +
                                    "are part of the game.",
                            ),
                    ),
                    RuleTopic(
                        id = "turn",
                        title = "What can I do on my turn?",
                        summary =
                            "Choose either an auction or a trade, depending on the game state.",
                        details =
                            listOf(
                                "At the beginning of the game, only auctions are possible " +
                                    "because players do not yet share matching animals.",
                                "Once two players own the same animal type, the active player " +
                                    "can offer a trade for that animal.",
                                "When the animal deck is empty, players must trade if a valid " +
                                    "trade is available.",
                            ),
                    ),
                ),
        ),
        RuleGroup(
            title = "Auction",
            topics =
                listOf(
                    RuleTopic(
                        id = "auction",
                        title = "How does an auction work?",
                        summary =
                            "Everyone except the auctioneer bids; the highest bidder wins " +
                                "unless the auctioneer buys back.",
                        details =
                            listOf(
                                "The auctioneer reveals the top animal card and opens bidding.",
                                "Other players may bid any amount, but each new bid must be " +
                                    "higher than the previous one.",
                                "When no one wants to bid higher, the auctioneer closes the " +
                                    "auction and the highest bidder receives the animal.",
                                "The highest bidder pays the auctioneer. If nobody bids, the " +
                                    "auctioneer may take the animal for free.",
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
                                "Immediately after the final bid, the auctioneer may decide " +
                                    "to buy the animal instead.",
                                "If they buy back, they pay the highest bidder the exact " +
                                    "winning amount and keep the animal card.",
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
                                "The four donkey payouts are 50, 100, 200, and 500 in order " +
                                    "of appearance.",
                                "After the money is distributed, the donkey is auctioned " +
                                    "like any other animal.",
                            ),
                    ),
                ),
        ),
        RuleGroup(
            title = "Trade",
            topics =
                listOf(
                    RuleTopic(
                        id = "trade",
                        title = "How does a trade work?",
                        summary =
                            "A player secretly offers money for a matching animal; " +
                                "the other player must counter.",
                        details =
                            listOf(
                                "A trade is possible when both players own at least one animal " +
                                    "of the same type.",
                                "The active player chooses the opponent and animal, then places " +
                                    "a hidden money offer.",
                                "The opponent cannot refuse. They place a hidden counter offer.",
                                "Both offers are revealed. The higher offer wins the animal " +
                                    "cards; the loser receives the winning money offer.",
                                "0 cards may be included to bluff and must be returned after " +
                                    "the trade.",
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
                                "If a bidder cannot pay the amount they bid, they reveal " +
                                    "all their money and the auction is repeated.",
                            ),
                    ),
                ),
        ),
        RuleGroup(
            title = "End Game",
            topics =
                listOf(
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
                ),
        ),
    )

private val animalValues =
    listOf(
        AnimalValue("Chicken", 10, R.drawable.hs_chicken),
        AnimalValue("Goose", 40, R.drawable.hs_goose),
        AnimalValue("Cat", 90, R.drawable.hs_cat),
        AnimalValue("Dog", 160, R.drawable.hs_dog),
        AnimalValue("Sheep", 250, R.drawable.hs_sheep),
        AnimalValue("Goat", 350, R.drawable.hs_goat),
        AnimalValue("Donkey", 500, R.drawable.hs_donkey),
        AnimalValue("Pig", 650, R.drawable.hs_pig),
        AnimalValue("Cow", 800, R.drawable.hs_cow),
        AnimalValue("Horse", 1000, R.drawable.hs_horse),
    )

private val firstGameTips =
    listOf(
        "Save enough money for later trades; the game usually gets sharper near the end.",
        "0 cards are useful for bluffing because opponents cannot read your offer value.",
        "Money is worth nothing at the end, so use it to win animals before scoring.",
        "The auctioneer cannot bid, but can buy back immediately after the auction closes.",
        "Donkeys give every player money before the donkey auction starts.",
        "Completing a low-value animal can still be better than overpaying for a horse.",
        "Watch who shares animals with you; those players can become trade targets.",
        "If nobody bids in an auction, the auctioneer can take the animal for free.",
        "A trade cannot be refused, so keep some money ready when others share your animals.",
        "You do not get change, so exact payment cards can be surprisingly valuable.",
        "Try not to reveal too much about how much money you still have.",
        "A strong counter offer can make the other player lose the animal instead.",
    )

private val tutorialSlides =
    listOf(
        TutorialSlide(
            title = "Collect Animal Sets",
            body =
                "Win animals through auctions and trades. Complete animal sets are safe, " +
                    "and only animal points count at the end.",
            action = TutorialAction.ANIMAL_VALUES,
            drawableIds =
                listOf(
                    R.drawable.hs_chicken,
                    R.drawable.hs_cow,
                    R.drawable.hs_horse,
                ),
        ),
        TutorialSlide(
            title = "Auction Phase",
            body =
                "The active player reveals an animal. Everyone else raises the bid with " +
                    "the +10, +50, or +100 buttons.",
            action = TutorialAction.BID_HIGHER,
            drawableIds =
                listOf(
                    R.drawable.auc_pig,
                    R.drawable.ig_money_revealed_50,
                    R.drawable.ig_money_revealed_100,
                ),
        ),
        TutorialSlide(
            title = "Auctioneer Buy-Back",
            body =
                "After the highest bid is set, the auctioneer may keep the animal by paying " +
                    "that amount to the highest bidder.",
            action = TutorialAction.BUY_BACK,
            drawableIds =
                listOf(
                    R.drawable.auc_cow,
                    R.drawable.ig_money_revealed_200,
                    R.drawable.ig_table,
                ),
        ),
        TutorialSlide(
            title = "Trade Offer",
            body =
                "If two players share an animal type, the active player can choose the " +
                    "opponent's farm and select the shared animal. After that, the trade starts.",
            action = TutorialAction.SELECT_TRADE,
            drawableIds =
                listOf(
                    R.drawable.hs_goat,
                    R.drawable.ig_money_hidden_table_3,
                    R.drawable.ig_table,
                ),
        ),
        TutorialSlide(
            title = "Counter Offer",
            body =
                "Select money cards from your hand and send them as a hidden offer. The " +
                    "higher offer wins the animal cards, and the loser receives the money.",
            action = TutorialAction.SEND_OFFER,
            drawableIds =
                listOf(
                    R.drawable.ig_money_hidden_table_4,
                    R.drawable.hs_sheep,
                    R.drawable.ig_money_hidden_table_counter_4,
                ),
        ),
        TutorialSlide(
            title = "Respond To A Trade",
            body =
                "When another player offers a trade, choose Take Offer if you accept it or " +
                    "Counter if you want to answer with your own hidden money cards.",
            action = TutorialAction.TRADE_RESPONSE,
            drawableIds = emptyList(),
        ),
        TutorialSlide(
            title = "Winner Screen",
            body =
                "When all animal sets are complete, the game ends. Money no longer counts; " +
                    "the winner screen ranks players by their final animal score.",
            action = TutorialAction.WIN_SCREEN,
            drawableIds = emptyList(),
        ),
    )

private val spyTutorialSlides =
    listOf(
        TutorialSlide(
            title = "Choose A Target",
            body =
                "When another player is choosing their action, tap one opponent farm to " +
                    "prepare a secret spy attempt.",
            action = TutorialAction.SPY_SELECT_FARM,
            drawableIds = emptyList(),
        ),
        TutorialSlide(
            title = "Arm The Eye",
            body =
                "An eye appears on that farm. Tap the eye to arm it before the short timer " +
                    "runs out.",
            action = TutorialAction.SPY_ARM_EYE,
            drawableIds = emptyList(),
        ),
        TutorialSlide(
            title = "Shake To Spy",
            body =
                "After the eye is armed, shake the phone to reveal the opponent's money cards.",
            action = TutorialAction.SPY_SHAKE,
            drawableIds = emptyList(),
        ),
        TutorialSlide(
            title = "Read The Money",
            body =
                "If the spy attempt succeeds, the target player's money cards are shown " +
                    "briefly in the middle of the screen.",
            action = TutorialAction.SPY_REVEAL,
            drawableIds = emptyList(),
        ),
        TutorialSlide(
            title = "Do Not Get Caught",
            body =
                "If someone spies on you, tap the large spy indicator while it is visible. " +
                    "A caught spy loses one random money card to you.",
            action = TutorialAction.SPY_CATCH,
            drawableIds = emptyList(),
        ),
    )

@Composable
fun RulesScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    var isTutorialOpen by rememberSaveable { mutableStateOf(false) }
    var isSpyTutorialOpen by rememberSaveable { mutableStateOf(false) }
    var tutorialSlideIndex by rememberSaveable { mutableStateOf(0) }
    var spyTutorialSlideIndex by rememberSaveable { mutableStateOf(0) }
    var expandedTopicId by
        rememberSaveable {
            mutableStateOf(
                ruleGroups
                    .first()
                    .topics
                    .first()
                    .id,
            )
        }

    MenuBackground(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp, bottom = 32.dp, start = 28.dp, end = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            MenuCard(
                onBack = {
                    if (isSpyTutorialOpen) {
                        isSpyTutorialOpen = false
                    } else if (isTutorialOpen) {
                        isTutorialOpen = false
                    } else {
                        onBack()
                    }
                },
            ) {
                if (isSpyTutorialOpen) {
                    TutorialSlideContent(
                        slide = spyTutorialSlides[spyTutorialSlideIndex],
                        currentIndex = spyTutorialSlideIndex,
                        totalSlides = spyTutorialSlides.size,
                        onPrevious = {
                            spyTutorialSlideIndex =
                                (spyTutorialSlideIndex - 1).coerceAtLeast(0)
                        },
                        onNext = {
                            if (spyTutorialSlideIndex == spyTutorialSlides.lastIndex) {
                                isSpyTutorialOpen = false
                                spyTutorialSlideIndex = 0
                            } else {
                                spyTutorialSlideIndex += 1
                            }
                        },
                    )
                } else if (isTutorialOpen) {
                    TutorialSlideContent(
                        slide = tutorialSlides[tutorialSlideIndex],
                        currentIndex = tutorialSlideIndex,
                        totalSlides = tutorialSlides.size,
                        onPrevious = {
                            tutorialSlideIndex = (tutorialSlideIndex - 1).coerceAtLeast(0)
                        },
                        onNext = {
                            if (tutorialSlideIndex == tutorialSlides.lastIndex) {
                                isTutorialOpen = false
                                tutorialSlideIndex = 0
                            } else {
                                tutorialSlideIndex += 1
                            }
                        },
                    )
                } else {
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

                        item {
                            FirstGameTips()
                        }

                        ruleGroups.forEach { group ->
                            item {
                                RuleGroupLabel(title = group.title)
                            }

                            items(group.topics) { topic ->
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
                        }

                        item {
                            AnimalValuesSection()
                        }

                        item {
                            TutorialTeaser(
                                onStartTutorial = {
                                    tutorialSlideIndex = 0
                                    isTutorialOpen = true
                                },
                            )
                        }
                    }
                }
            }
            if (!isTutorialOpen && !isSpyTutorialOpen) {
                HiddenSpyTutorialButton(
                    onClick = {
                        spyTutorialSlideIndex = 0
                        isSpyTutorialOpen = true
                    },
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun RuleGroupLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = DarkPurple,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 8.dp, end = 8.dp),
    )
}

@Composable
private fun FirstGameTips() {
    var tipStartIndex by rememberSaveable { mutableStateOf(0) }
    val visibleTips =
        List(FIRST_GAME_TIP_COUNT) { offset ->
            firstGameTips[(tipStartIndex + offset) % firstGameTips.size]
        }

    RuleInfoCard(title = "First Game Tips") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(
                onClick = {
                    tipStartIndex = (tipStartIndex + FIRST_GAME_TIP_COUNT) % firstGameTips.size
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh tips",
                    tint = DarkPurple,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        visibleTips.forEach { tip ->
            RuleBullet(tip)
        }
    }
}

@Composable
private fun AnimalValuesSection() {
    RuleInfoCard(title = "Animal Values") {
        animalValues.chunked(2).forEach { rowAnimals ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowAnimals.forEach { animal ->
                    AnimalValueItem(
                        animal = animal,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowAnimals.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RuleInfoCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(WhitePurple)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = DarkPurple,
            fontWeight = FontWeight.Bold,
        )
        content()
    }
}

@Composable
private fun AnimalValueItem(
    animal: AnimalValue,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(id = animal.drawableId),
            contentDescription = animal.name,
            modifier = Modifier.size(34.dp),
            contentScale = ContentScale.Fit,
        )
        Column {
            Text(
                text = animal.name,
                style = MaterialTheme.typography.bodyMedium,
                color = DarkPurple,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${animal.points} points",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    contentDescription: String?,
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
private fun TutorialTeaser(onStartTutorial: () -> Unit) {
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
                "Walk through the game flow with a short slide tutorial for auctions, " +
                    "trades, donkeys, and scoring.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onStartTutorial) {
            Text("Start Tutorial")
        }
    }
}

@Composable
private fun HiddenSpyTutorialButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(36.dp)
                .alpha(0.58f)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "👀",
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun TutorialSlideContent(
    slide: TutorialSlide,
    currentIndex: Int,
    totalSlides: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Tutorial ${currentIndex + 1} / $totalSlides",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = slide.title,
                style = MaterialTheme.typography.headlineMedium,
                color = DarkPurple,
                fontWeight = FontWeight.Bold,
            )
            TutorialUiPreview(slide)
            Text(
                text = slide.body,
                style = MaterialTheme.typography.bodyLarge,
                color = DarkPurple,
            )
        }

        TutorialProgressDots(
            currentIndex = currentIndex,
            totalSlides = totalSlides,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onPrevious,
                enabled = currentIndex > 0,
                modifier = Modifier.weight(1f),
            ) {
                Text("Back")
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (currentIndex == totalSlides - 1) {
                        "Done"
                    } else {
                        "Next"
                    },
                )
            }
        }
    }
}

@Composable
private fun TutorialUiPreview(slide: TutorialSlide) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = WhitePurple,
        border = BorderStroke(2.dp, LightPurple),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "In the game",
                style = MaterialTheme.typography.labelLarge,
                color = DarkPurple,
                fontWeight = FontWeight.Bold,
            )
            when (slide.action) {
                TutorialAction.ANIMAL_VALUES -> TutorialAnimalValuePreview()
                TutorialAction.BID_HIGHER -> TutorialBidPreview(slide.drawableIds.first())
                TutorialAction.BUY_BACK -> TutorialBuyBackPreview(slide.drawableIds.first())
                TutorialAction.SELECT_TRADE -> TutorialTradeTargetPreview()
                TutorialAction.SEND_OFFER -> TutorialSendOfferPreview()
                TutorialAction.TRADE_RESPONSE -> TutorialTradeResponsePreview()
                TutorialAction.WIN_SCREEN -> TutorialWinScreenPreview()
                TutorialAction.SPY_SELECT_FARM -> SpySelectFarmPreview()
                TutorialAction.SPY_ARM_EYE -> SpyArmEyePreview()
                TutorialAction.SPY_SHAKE -> SpyShakePreview()
                TutorialAction.SPY_REVEAL -> SpyRevealPreview()
                TutorialAction.SPY_CATCH -> SpyCatchPreview()
            }
        }
    }
}

@Composable
private fun TutorialAnimalValuePreview() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimalValuePreviewItem("Chicken", 10, R.drawable.hs_chicken)
        AnimalValuePreviewItem("Cow", 800, R.drawable.hs_cow)
        AnimalValuePreviewItem("Horse", 1000, R.drawable.hs_horse)
    }
}

@Composable
private fun AnimalValuePreviewItem(
    name: String,
    points: Int,
    drawableId: Int,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RuleAsset(drawableId, name)
        Text(
            text = "$points pts",
            style = MaterialTheme.typography.labelMedium,
            color = DarkPurple,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TutorialBidPreview(animalDrawableId: Int) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RuleAsset(animalDrawableId, "auction animal")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 50, 100).forEach { amount ->
                if (amount == 50) {
                    TutorialClickTarget {
                        TutorialActionButton("+$amount")
                    }
                } else {
                    TutorialActionButton("+$amount")
                }
            }
        }
    }
}

@Composable
private fun TutorialBuyBackPreview(animalDrawableId: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RuleAsset(animalDrawableId, "auction animal")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TutorialClickTarget {
                Button(onClick = {}) {
                    Text("Buy Back")
                }
            }
            Button(onClick = {}) {
                Text("Let Winner Buy")
            }
        }
    }
}

@Composable
private fun TutorialTradeTargetPreview() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TutorialClickTarget {
            RuleAsset(R.drawable.ig_farm_blue, "opponent farm")
        }
        Text(
            text = "then",
            style = MaterialTheme.typography.labelMedium,
            color = DarkPurple,
            fontWeight = FontWeight.Bold,
        )
        TutorialAnimalCircle()
    }
}

@Composable
private fun TutorialSendOfferPreview() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Select",
            style = MaterialTheme.typography.titleLarge,
            color = DarkPurple,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                MoneyCard("tutorial-10", 10),
                MoneyCard("tutorial-50", 50),
                MoneyCard("tutorial-100", 100),
            ).forEach { card ->
                if (card.value == 50) {
                    TutorialClickTarget(
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    ) {
                        MoneyCardView(
                            card = card,
                            isSelected = true,
                            onClick = {},
                            isClickable = false,
                        )
                    }
                } else {
                    MoneyCardView(
                        card = card,
                        isSelected = card.value == 10,
                        onClick = {},
                        isClickable = false,
                    )
                }
            }
        }
        TutorialClickTarget(modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = {}) {
                Text("Send Offer (2)")
            }
        }
        HiddenOfferPreview(
            label = "Hidden on table",
            drawableId = R.drawable.ig_money_hidden_table_2,
        )
    }
}

@Composable
private fun TutorialTradeResponsePreview() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = {}) {
            Text("Take Offer")
        }
        TutorialClickTarget {
            Button(onClick = {}) {
                Text("Counter")
            }
        }
    }
}

@Composable
private fun SpySelectFarmPreview() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TutorialClickTarget {
            RuleAsset(R.drawable.ig_farm_red, "opponent farm")
        }
        RuleAsset(R.drawable.spy_eye, "spy eye")
    }
}

@Composable
private fun SpyArmEyePreview() {
    Box(
        modifier = Modifier.size(width = 142.dp, height = 104.dp),
        contentAlignment = Alignment.Center,
    ) {
        RuleAsset(R.drawable.ig_farm_red, "opponent farm")
        TutorialClickTarget(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 18.dp, y = 4.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.spy_eye),
                contentDescription = "spy eye",
                modifier = Modifier.size(34.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun SpyShakePreview() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.spy_eye),
            contentDescription = "armed spy eye",
            modifier = Modifier.size(58.dp),
            contentScale = ContentScale.Fit,
        )
        TutorialActionButton("Shake phone")
    }
}

@Composable
private fun SpyRevealPreview() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            R.drawable.ig_money_revealed_10,
            R.drawable.ig_money_revealed_50,
            R.drawable.ig_money_revealed_100,
        ).forEach { drawableId ->
            Image(
                painter = painterResource(id = drawableId),
                contentDescription = null,
                modifier = Modifier.size(width = 44.dp, height = 64.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun SpyCatchPreview() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TutorialClickTarget {
            Image(
                painter = painterResource(id = R.drawable.spy_indicator_white),
                contentDescription = "catch spy",
                modifier = Modifier.size(76.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Spy",
                style = MaterialTheme.typography.labelMedium,
                color = DarkPurple,
                fontWeight = FontWeight.Bold,
            )
            Image(
                painter = painterResource(id = R.drawable.ig_money_revealed_50),
                contentDescription = null,
                modifier = Modifier.size(width = 32.dp, height = 46.dp),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = "->",
                style = MaterialTheme.typography.titleMedium,
                color = DarkPurple,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "You",
                style = MaterialTheme.typography.labelMedium,
                color = DarkPurple,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TutorialWinScreenPreview() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = WhitePurple,
        border = BorderStroke(1.dp, LightPurple),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "WINNER",
                style = MaterialTheme.typography.titleLarge,
                color = DarkPurple,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "PLAYER 1",
                style = MaterialTheme.typography.headlineSmall,
                color = DarkPurple,
                fontWeight = FontWeight.Bold,
            )
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = DarkPurple,
            ) {
                Text(
                    text = "1820p",
                    style = MaterialTheme.typography.labelLarge,
                    color = WhitePurple,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                listOf(
                    R.drawable.ig_horse,
                    R.drawable.ig_cow,
                    R.drawable.ig_pig,
                ).forEach { drawableId ->
                    Image(
                        painter = painterResource(id = drawableId),
                        contentDescription = null,
                        modifier = Modifier.size(width = 44.dp, height = 58.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WinnerRankPreview("2", "Player 2", "1280p")
                WinnerRankPreview("3", "Player 3", "940p")
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = LightPurple.copy(alpha = 0.7f),
            ) {
                Text(
                    text = "HOME",
                    style = MaterialTheme.typography.labelLarge,
                    color = DarkPurple,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 34.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun WinnerRankPreview(
    rank: String,
    name: String,
    points: String,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = LightPurple.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, LightPurple),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.labelMedium,
                color = DarkPurple,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = DarkPurple,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = points,
                style = MaterialTheme.typography.labelSmall,
                color = DarkPurple,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TutorialActionButton(label: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = LightPurple.copy(alpha = 0.74f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = DarkPurple,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun TutorialClickTarget(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.padding(4.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = WhitePurple,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(2.dp, LightPurple.copy(alpha = 0.82f)),
        ) {
            Box(modifier = Modifier.padding(5.dp)) {
                content()
            }
        }
        Surface(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 10.dp)
                    .size(30.dp),
            shape = CircleShape,
            color = WhitePurple,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, LightPurple),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "👆",
                    fontSize = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun TutorialAnimalCircle() {
    Box(
        modifier = Modifier.size(116.dp),
        contentAlignment = Alignment.Center,
    ) {
        val animals =
            listOf(
                R.drawable.hs_chicken,
                R.drawable.hs_goat,
                R.drawable.hs_cow,
                R.drawable.hs_sheep,
                R.drawable.hs_horse,
                R.drawable.hs_pig,
            )

        animals.forEachIndexed { index, drawableId ->
            val isShared = drawableId == R.drawable.hs_goat || drawableId == R.drawable.hs_sheep
            val xOffset =
                when (index) {
                    0 -> 0.dp
                    1 -> 44.dp
                    2 -> 44.dp
                    3 -> 0.dp
                    4 -> (-44).dp
                    else -> (-44).dp
                }
            val yOffset =
                when (index) {
                    0 -> (-48).dp
                    1 -> (-24).dp
                    2 -> 24.dp
                    3 -> 48.dp
                    4 -> 24.dp
                    else -> (-24).dp
                }

            Box(
                modifier =
                    Modifier
                        .offset(x = xOffset, y = yOffset)
                        .alpha(if (isShared) 1f else 0.35f),
            ) {
                if (drawableId == R.drawable.hs_goat) {
                    TutorialClickTarget {
                        TutorialAnimalCircleItem(drawableId, isShared)
                    }
                } else {
                    TutorialAnimalCircleItem(drawableId, isShared)
                }
            }
        }
    }
}

@Composable
private fun TutorialAnimalCircleItem(
    drawableId: Int,
    isShared: Boolean,
) {
    Surface(
        modifier = Modifier.size(34.dp),
        shape = CircleShape,
        color = Color.White,
        border =
            if (isShared) {
                BorderStroke(2.dp, LightPurple)
            } else {
                null
            },
    ) {
        Image(
            painter = painterResource(id = drawableId),
            contentDescription = null,
            modifier = Modifier.padding(4.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun HiddenOfferPreview(
    label: String,
    drawableId: Int,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RuleAsset(drawableId, label)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = DarkPurple,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TutorialProgressDots(
    currentIndex: Int,
    totalSlides: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSlides) { index ->
            Box(
                modifier =
                    Modifier
                        .size(if (index == currentIndex) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == currentIndex) {
                                DarkPurple
                            } else {
                                LightPurple
                            },
                        ),
            )
        }
    }
}
