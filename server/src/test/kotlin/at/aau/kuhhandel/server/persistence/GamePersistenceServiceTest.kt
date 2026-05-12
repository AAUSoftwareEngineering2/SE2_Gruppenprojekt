package at.aau.kuhhandel.server.persistence

import at.aau.kuhhandel.server.persistence.entity.GameStatus
import at.aau.kuhhandel.server.persistence.repository.AuctionStateRepository
import at.aau.kuhhandel.server.persistence.repository.DeckCardRepository
import at.aau.kuhhandel.server.persistence.repository.GamePlayerRepository
import at.aau.kuhhandel.server.persistence.repository.GameRepository
import at.aau.kuhhandel.server.persistence.repository.PlayerAnimalRepository
import at.aau.kuhhandel.server.persistence.repository.PlayerMoneyRepository
import at.aau.kuhhandel.server.persistence.repository.TradeStateRepository
import at.aau.kuhhandel.server.persistence.repository.UserRepository
import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.enums.GamePhase
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.AnimalDeck
import at.aau.kuhhandel.shared.model.AuctionState
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.PlayerState
import at.aau.kuhhandel.shared.model.TradeState
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DataJpaTest
@ActiveProfiles("test")
@Import(GamePersistenceService::class)
class GamePersistenceServiceTest
    @Autowired
    constructor(
        private val service: GamePersistenceService,
        private val gameRepository: GameRepository,
        private val userRepository: UserRepository,
        private val gamePlayerRepository: GamePlayerRepository,
        private val deckCardRepository: DeckCardRepository,
        private val playerMoneyRepository: PlayerMoneyRepository,
        private val playerAnimalRepository: PlayerAnimalRepository,
        private val auctionStateRepository: AuctionStateRepository,
        private val tradeStateRepository: TradeStateRepository,
    ) {
        @Test
        fun `loadGameState returns null for unknown game`() {
            assertNull(service.loadGameState("12345"))
        }

        @Test
        fun `loadGameState returns null for non-numeric ids`() {
            assertNull(service.loadGameState("abc"))
        }

        @Test
        fun `saveGameState persists a freshly created lobby game`() {
            service.saveGameState("12345", initialLobbyState())

            val game = gameRepository.findById(12345L).orElseThrow()
            assertEquals(GameStatus.LOBBY, game.status)
            assertEquals(1, gamePlayerRepository.findByGameOrderBySeatOrderAsc(game).size)
            assertEquals("player-1", game.activePlayer?.username)
        }

        @Test
        fun `saveGameState aggregates money and animal cards per player`() {
            val state =
                initialLobbyState().copy(
                    phase = GamePhase.PLAYER_TURN,
                    players =
                        listOf(
                            PlayerState(
                                id = "player-1",
                                name = "player-1",
                                animals =
                                    listOf(
                                        AnimalCard(id = "a1", type = AnimalType.CHICKEN),
                                        AnimalCard(id = "a2", type = AnimalType.CHICKEN),
                                        AnimalCard(id = "a3", type = AnimalType.GOOSE),
                                    ),
                                moneyCards =
                                    listOf(
                                        MoneyCard(id = "m1", value = 10),
                                        MoneyCard(id = "m2", value = 10),
                                        MoneyCard(id = "m3", value = 50),
                                    ),
                            ),
                        ),
                )
            service.saveGameState("12345", state)

            val player =
                gamePlayerRepository
                    .findByGameOrderBySeatOrderAsc(gameRepository.findById(12345L).orElseThrow())
                    .single()
            val money =
                playerMoneyRepository.findByPlayer(player).associate {
                    it.cardValue to
                        it.amount
                }
            val animals =
                playerAnimalRepository.findByPlayer(player).associate {
                    it.animalType to
                        it.amount
                }

            assertEquals(mapOf(10 to 2, 50 to 1), money)
            assertEquals(mapOf(AnimalType.CHICKEN to 2, AnimalType.GOOSE to 1), animals)
        }

        @Test
        fun `loadGameState restores the lobby snapshot`() {
            service.saveGameState("12345", initialLobbyState())

            val loaded = assertNotNull(service.loadGameState("12345"))
            assertEquals(GamePhase.NOT_STARTED, loaded.phase)
            assertEquals(1, loaded.players.size)
            assertEquals("player-1", loaded.players.single().id)
            assertEquals(0, loaded.currentPlayerIndex)
        }

        @Test
        fun `saveGameState persists an active auction snapshot`() {
            val state =
                initialLobbyState().copy(
                    phase = GamePhase.AUCTION,
                    players =
                        listOf(
                            PlayerState(id = "player-1", name = "player-1"),
                            PlayerState(id = "player-2", name = "player-2"),
                        ),
                    currentPlayerIndex = 0,
                    auctionState =
                        AuctionState(
                            auctionCard = AnimalCard(id = "card-1", type = AnimalType.COW),
                            auctioneerId = "player-1",
                            highestBid = 80,
                            highestBidderId = "player-2",
                        ),
                )
            service.saveGameState("12345", state)

            val auction = auctionStateRepository.findById(12345L).orElseThrow()
            assertEquals(AnimalType.COW, auction.currentAnimal)
            assertEquals(80, auction.highestBid)
            assertEquals("player-2", auction.highestBidder?.user?.username)
        }

        @Test
        fun `loaded auction state mirrors the persisted snapshot`() {
            val state =
                initialLobbyState().copy(
                    phase = GamePhase.AUCTION,
                    players =
                        listOf(
                            PlayerState(id = "player-1", name = "player-1"),
                            PlayerState(id = "player-2", name = "player-2"),
                        ),
                    currentPlayerIndex = 0,
                    auctionState =
                        AuctionState(
                            auctionCard = AnimalCard(id = "card-1", type = AnimalType.HORSE),
                            auctioneerId = "player-1",
                            highestBid = 200,
                            highestBidderId = "player-2",
                        ),
                )
            service.saveGameState("12345", state)

            val loaded = assertNotNull(service.loadGameState("12345"))
            assertEquals(GamePhase.AUCTION, loaded.phase)
            assertEquals(AnimalType.HORSE, loaded.auctionState?.auctionCard?.type)
            assertEquals(200, loaded.auctionState?.highestBid)
            assertEquals("player-2", loaded.auctionState?.highestBidderId)
            assertEquals("player-1", loaded.auctionState?.auctioneerId)
        }

        @Test
        fun `saveGameState clears persisted auction when game leaves auction phase`() {
            val withAuction =
                initialLobbyState().copy(
                    phase = GamePhase.AUCTION,
                    players = listOf(PlayerState(id = "player-1", name = "player-1")),
                    auctionState =
                        AuctionState(
                            auctionCard = AnimalCard(id = "card-1", type = AnimalType.CAT),
                            auctioneerId = "player-1",
                        ),
                )
            service.saveGameState("12345", withAuction)
            assertNotNull(auctionStateRepository.findById(12345L).orElse(null))

            service.saveGameState("12345", initialLobbyState())
            assertNull(auctionStateRepository.findById(12345L).orElse(null))
        }

        @Test
        fun `saveGameState persists trade offers as JSON values`() {
            val state =
                initialLobbyState().copy(
                    phase = GamePhase.TRADE,
                    players =
                        listOf(
                            PlayerState(
                                id = "player-1",
                                name = "player-1",
                                moneyCards =
                                    listOf(
                                        MoneyCard(id = "m1", value = 10),
                                        MoneyCard(id = "m2", value = 50),
                                    ),
                            ),
                            PlayerState(
                                id = "player-2",
                                name = "player-2",
                                moneyCards = listOf(MoneyCard(id = "m3", value = 20)),
                            ),
                        ),
                    tradeState =
                        TradeState(
                            initiatingPlayerId = "player-1",
                            challengedPlayerId = "player-2",
                            requestedAnimalType = AnimalType.DOG,
                            offeredMoney = 60,
                            offeredMoneyCardIds = listOf("m1", "m2"),
                            counterOfferedMoney = 20,
                            counterOfferedMoneyCardIds = listOf("m3"),
                        ),
                )
            service.saveGameState("12345", state)

            val trade = tradeStateRepository.findById(12345L).orElseThrow()
            assertEquals(AnimalType.DOG, trade.animalType)
            assertEquals("[10,50]", trade.challengerOfferJson)
            assertEquals("[20]", trade.defenderOfferJson)
        }

        @Test
        fun `saveGameState rewrites deck and player inventory on subsequent saves`() {
            val initial =
                initialLobbyState().copy(
                    deck = AnimalDeck(listOf(AnimalCard(id = "1", type = AnimalType.COW))),
                    players =
                        listOf(
                            PlayerState(
                                id = "player-1",
                                name = "player-1",
                                animals = listOf(AnimalCard(id = "a1", type = AnimalType.CHICKEN)),
                                moneyCards = listOf(MoneyCard(id = "m1", value = 10)),
                            ),
                        ),
                )
            service.saveGameState("12345", initial)

            val updated =
                initial.copy(
                    deck =
                        AnimalDeck(
                            listOf(
                                AnimalCard(id = "1", type = AnimalType.DOG),
                                AnimalCard(id = "2", type = AnimalType.CAT),
                            ),
                        ),
                    players =
                        listOf(
                            PlayerState(
                                id = "player-1",
                                name = "player-1",
                                animals =
                                    listOf(
                                        AnimalCard(id = "a1", type = AnimalType.GOOSE),
                                        AnimalCard(id = "a2", type = AnimalType.GOOSE),
                                    ),
                                moneyCards = emptyList(),
                            ),
                        ),
                )
            service.saveGameState("12345", updated)

            val game = gameRepository.findById(12345L).orElseThrow()
            val deck = deckCardRepository.findByGameOrderByDrawOrderAsc(game)
            assertEquals(listOf(AnimalType.DOG, AnimalType.CAT), deck.map { it.animalType })

            val player = gamePlayerRepository.findByGameOrderBySeatOrderAsc(game).single()
            val animals =
                playerAnimalRepository.findByPlayer(player).associate {
                    it.animalType to
                        it.amount
                }
            assertEquals(mapOf(AnimalType.GOOSE to 2), animals)
            assertEquals(
                emptyList<Int>(),
                playerMoneyRepository.findByPlayer(player).map { it.cardValue },
            )
        }

        @Test
        fun `deleteGame removes a persisted game and its children`() {
            service.saveGameState("12345", initialLobbyState())
            assertNotNull(gameRepository.findById(12345L).orElse(null))

            service.deleteGame("12345")

            assertNull(gameRepository.findById(12345L).orElse(null))
            assertNull(userRepository.findByUsername("non-existing-user"))
        }

        private fun initialLobbyState(): GameState =
            GameState(
                phase = GamePhase.NOT_STARTED,
                players = listOf(PlayerState(id = "player-1", name = "player-1")),
            )
    }
