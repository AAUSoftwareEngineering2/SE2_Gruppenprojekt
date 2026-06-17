package at.aau.kuhhandel.server.persistence.mapper

import at.aau.kuhhandel.server.persistence.entity.AuctionStateEntity
import at.aau.kuhhandel.server.persistence.entity.GameEntity
import at.aau.kuhhandel.server.persistence.entity.GamePlayerEntity
import at.aau.kuhhandel.server.persistence.entity.TradeStateEntity
import at.aau.kuhhandel.server.persistence.entity.UserEntity
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.MoneyCard
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import at.aau.kuhhandel.server.persistence.entity.GameStatus as PersistedStatus

class GameStateMapperTest {
    @Test
    fun `auction bidding and resolution phases both map to AUCTION status`() {
        kotlin.test.assertEquals(
            PersistedStatus.AUCTION,
            GameStateMapper.toGameStatus(GamePhase.AUCTION_BIDDING),
        )
        kotlin.test.assertEquals(
            PersistedStatus.AUCTION,
            GameStateMapper.toGameStatus(GamePhase.AUCTIONEER_DECISION),
        )
        kotlin.test.assertEquals(
            PersistedStatus.AUCTION,
            GameStateMapper.toGameStatus(GamePhase.AUCTION_PAYMENT),
        )
        kotlin.test.assertEquals(
            PersistedStatus.AUCTION,
            GameStateMapper.toGameStatus(GamePhase.AUCTION_RESULT),
        )
    }

    @Test
    fun `all trade phases map to TRADE status`() {
        kotlin.test.assertEquals(
            PersistedStatus.TRADE,
            GameStateMapper.toGameStatus(GamePhase.TRADE_OFFER),
        )
        kotlin.test.assertEquals(
            PersistedStatus.TRADE,
            GameStateMapper.toGameStatus(GamePhase.TRADE_RESPONSE),
        )
        kotlin.test.assertEquals(
            PersistedStatus.TRADE,
            GameStateMapper.toGameStatus(GamePhase.TRADE_RESULT),
        )
    }

    @Test
    fun `finished phase maps to FINISHED status`() {
        kotlin.test.assertEquals(
            PersistedStatus.FINISHED,
            GameStateMapper.toGameStatus(GamePhase.FINISHED),
        )
    }

    @Test
    fun `not started and player choice both collapse to LOBBY`() {
        kotlin.test.assertEquals(
            PersistedStatus.LOBBY,
            GameStateMapper.toGameStatus(GamePhase.NOT_STARTED),
        )
        kotlin.test.assertEquals(
            PersistedStatus.LOBBY,
            GameStateMapper.toGameStatus(GamePhase.PLAYER_CHOICE),
        )
    }

    @Test
    fun `encode and decode of int lists round-trips`() {
        val values = listOf(10, 20, 30, 0, 50)
        val encoded = GameStateMapper.encodeIntList(values)
        kotlin.test.assertEquals(values, GameStateMapper.decodeIntList(encoded))
    }

    @Test
    fun `decode of null or blank JSON returns empty`() {
        kotlin.test.assertEquals(emptyList<Int>(), GameStateMapper.decodeIntList(null))
        kotlin.test.assertEquals(emptyList<Int>(), GameStateMapper.decodeIntList(""))
        kotlin.test.assertEquals(emptyList<String>(), GameStateMapper.decodeStringList(""))
    }

    @Test
    fun `encode and decode of string lists round-trips`() {
        val values = listOf("player-1", "player-2", "player-3")
        val encoded = GameStateMapper.encodeStringList(values)
        kotlin.test.assertEquals(values, GameStateMapper.decodeStringList(encoded))
    }

    @Test
    fun `auction state falls back to active player when auctioneer id is missing`() {
        val activeUser =
            UserEntity(
                username = "active-user",
                passwordHash = "active-player",
                id = 100L,
            )
        val game =
            GameEntity(
                id = 12345L,
                phase = GamePhase.AUCTION_BIDDING,
                activePlayer = activeUser,
            )
        val activePlayer =
            GamePlayerEntity(
                game = game,
                user = activeUser,
                playerId = "active-player",
                displayName = "Active",
                seatOrder = 0,
                id = 200L,
            )
        val auction =
            AuctionStateEntity(
                game = game,
                currentAnimal = AnimalType.COW,
                auctioneerPlayerId = null,
            )

        val state =
            GameStateMapper.toGameState(
                game = game,
                players = listOf(activePlayer),
                animalsByPlayer = emptyMap(),
                moneyByPlayer = emptyMap(),
                deck = emptyList(),
                auction = auction,
                trade = null,
            )

        assertEquals("active-player", state.auctionState?.auctioneerId)
    }

    @Test
    fun `auction payment buyer and seller ids restore from persisted auction state`() {
        val game =
            GameEntity(
                id = 12345L,
                phase = GamePhase.AUCTION_PAYMENT,
            )
        val buyerUser =
            UserEntity(
                username = "buyer-user",
                passwordHash = "buyer",
                id = 101L,
            )
        val sellerUser =
            UserEntity(
                username = "seller-user",
                passwordHash = "seller",
                id = 102L,
            )
        val buyer =
            GamePlayerEntity(
                game = game,
                user = buyerUser,
                playerId = "buyer",
                displayName = "Buyer",
                seatOrder = 0,
                id = 201L,
            )
        val seller =
            GamePlayerEntity(
                game = game,
                user = sellerUser,
                playerId = "seller",
                displayName = "Seller",
                seatOrder = 1,
                id = 202L,
            )
        val auction =
            AuctionStateEntity(
                game = game,
                currentAnimal = AnimalType.COW,
                auctioneerPlayerId = "seller",
                highestBid = 20,
                highestBidder = buyer,
                buyerPlayerId = "buyer",
                sellerPlayerId = "seller",
            )

        val state =
            GameStateMapper.toGameState(
                game = game,
                players = listOf(buyer, seller),
                animalsByPlayer = emptyMap(),
                moneyByPlayer = emptyMap(),
                deck = emptyList(),
                auction = auction,
                trade = null,
            )

        assertEquals("buyer", state.auctionState?.buyerId)
        assertEquals("seller", state.auctionState?.sellerId)
    }

    @Test
    fun `legacy trade money values expand to deterministic money cards`() {
        val game =
            GameEntity(
                id = 12345L,
                phase = GamePhase.TRADE_RESULT,
            )
        val challengerUser =
            UserEntity(
                username = "challenger-user",
                passwordHash = "challenger",
                id = 101L,
            )
        val defenderUser =
            UserEntity(
                username = "defender-user",
                passwordHash = "defender",
                id = 102L,
            )
        val challenger =
            GamePlayerEntity(
                game = game,
                user = challengerUser,
                playerId = "challenger",
                displayName = "Challenger",
                seatOrder = 0,
                id = 201L,
            )
        val defender =
            GamePlayerEntity(
                game = game,
                user = defenderUser,
                playerId = "defender",
                displayName = "Defender",
                seatOrder = 1,
                id = 202L,
            )
        val trade =
            TradeStateEntity(
                game = game,
                challenger = challenger,
                defender = defender,
                animalType = AnimalType.COW,
                challengerOfferJson = GameStateMapper.encodeIntList(listOf(10, 20)),
                defenderOfferJson = GameStateMapper.encodeIntList(listOf(5, 5)),
                challengerOfferCardsJson = null,
                defenderOfferCardsJson = null,
            )

        val state =
            GameStateMapper.toGameState(
                game = game,
                players = listOf(challenger, defender),
                animalsByPlayer = emptyMap(),
                moneyByPlayer = emptyMap(),
                deck = emptyList(),
                auction = null,
                trade = trade,
            )
        val loadedTrade = assertNotNull(state.tradeState)

        assertEquals(
            setOf(
                MoneyCard(id = "p201-m-10-0-12345", value = 10),
                MoneyCard(id = "p201-m-20-0-12345", value = 20),
            ),
            loadedTrade.offeredMoneyCards,
        )
        assertEquals(
            setOf(
                MoneyCard(id = "p202-m-5-0-12345", value = 5),
                MoneyCard(id = "p202-m-5-1-12345", value = 5),
            ),
            loadedTrade.counterOfferedMoneyCards,
        )
    }
}
