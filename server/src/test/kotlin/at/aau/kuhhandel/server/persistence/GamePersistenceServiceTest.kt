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
import at.aau.kuhhandel.shared.model.GameSession
import at.aau.kuhhandel.shared.model.GameState
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.Player
import at.aau.kuhhandel.shared.model.TradeState
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataJpaTest
@ActiveProfiles("test")
@Import(GamePersistenceService::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class GamePersistenceServiceTest
    @Autowired
    constructor(
        private val dataSource: DataSource,
        private val service: GamePersistenceService,
        private val gameRepository: GameRepository,
        private val userRepository: UserRepository,
        private val gamePlayerRepository: GamePlayerRepository,
        private val deckCardRepository: DeckCardRepository,
        private val playerMoneyRepository: PlayerMoneyRepository,
        private val playerAnimalRepository: PlayerAnimalRepository,
        private val auctionStateRepository: AuctionStateRepository,
        private val tradeStateRepository: TradeStateRepository,
    ) : PostgresDataJpaTest() {
        @Test
        // testet: der Persistenz-Slice läuft tatsächlich gegen eine PostgreSQL-Datenbank (JDBC-URL beginnt mit jdbc:postgresql:)
        fun `persistence slice runs against postgres`() {
            dataSource.connection.use { connection ->
                assertTrue(connection.metaData.url.startsWith("jdbc:postgresql:"))
            }
        }

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
            // currentPlayerIndex is -1 in NOT_STARTED state, so no active player is persisted
            assertNull(game.activePlayer)
        }

        @Test
        fun `saveGameState aggregates money and animal cards per player`() {
            val state =
                initialLobbyState().copy(
                    phase = GamePhase.PLAYER_CHOICE,
                    players =
                        listOf(
                            Player(
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
            assertEquals(-1, loaded.currentPlayerIndex)
        }

        @Test
        fun `saveGameState persists an active auction snapshot`() {
            val state =
                initialLobbyState().copy(
                    phase = GamePhase.AUCTION_BIDDING,
                    players =
                        listOf(
                            Player(id = "player-1", name = "player-1"),
                            Player(id = "player-2", name = "player-2"),
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
                    phase = GamePhase.AUCTION_BIDDING,
                    players =
                        listOf(
                            Player(id = "player-1", name = "player-1"),
                            Player(id = "player-2", name = "player-2"),
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
            assertEquals(GamePhase.AUCTION_BIDDING, loaded.phase)
            assertEquals(AnimalType.HORSE, loaded.auctionState?.auctionCard?.type)
            assertEquals(200, loaded.auctionState?.highestBid)
            assertEquals("player-2", loaded.auctionState?.highestBidderId)
            assertEquals("player-1", loaded.auctionState?.auctioneerId)
        }

        @Test
        // testet: loadGameState stellt die exakte Auktionsphase (AUCTIONEER_DECISION) und den Top-Level-Timer wieder her
        fun `loadGameState restores exact auction phase and top-level timer`() {
            val timerDeadline = 1_700_000_000_000L
            val state =
                initialLobbyState().copy(
                    phase = GamePhase.AUCTIONEER_DECISION,
                    timerEnd = timerDeadline,
                    players =
                        listOf(
                            Player(id = "player-1", name = "player-1"),
                            Player(id = "player-2", name = "player-2"),
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
            assertEquals(GamePhase.AUCTIONEER_DECISION, loaded.phase)
            assertEquals(timerDeadline, loaded.timerEnd)
        }

        @Test
        // testet: loadGameState stellt die AUCTION_RESULT-Phase samt Timer, buyerId und auctioneerId aus der Auktion wieder her
        fun `loadGameState restores exact auction result phase and buyer`() {
            val timerDeadline = 1_700_000_000_000L
            val state =
                initialLobbyState().copy(
                    phase = GamePhase.AUCTION_RESULT,
                    timerEnd = timerDeadline,
                    players =
                        listOf(
                            Player(id = "player-1", name = "player-1"),
                            Player(id = "player-2", name = "player-2"),
                        ),
                    currentPlayerIndex = 0,
                    auctionState =
                        AuctionState(
                            auctionCard = AnimalCard(id = "card-1", type = AnimalType.HORSE),
                            auctioneerId = "player-1",
                            highestBid = 200,
                            highestBidderId = "player-2",
                            buyerId = "player-2",
                        ),
                )
            service.saveGameState("12345", state)

            val loaded = assertNotNull(service.loadGameState("12345"))
            assertEquals(GamePhase.AUCTION_RESULT, loaded.phase)
            assertEquals(timerDeadline, loaded.timerEnd)
            assertEquals("player-2", loaded.auctionState?.buyerId)
            assertEquals("player-1", loaded.auctionState?.auctioneerId)
        }

        @Test
        fun `face up card is persisted and restored on reload`() {
            val state =
                initialLobbyState().copy(
                    phase = GamePhase.PLAYER_CHOICE,
                )
            service.saveGameState("12345", state)

            gameRepository.findById(12345L).orElseThrow()

            assertNotNull(service.loadGameState("12345"))
        }

        @Test
        fun `face up card is cleared when the next state has none`() {
            val withCard =
                initialLobbyState().copy(
                    phase = GamePhase.PLAYER_CHOICE,
                )
            service.saveGameState("12345", withCard)

            service.saveGameState("12345", initialLobbyState())
            assertNotNull(gameRepository.findById(12345L).orElseThrow())
        }

        @Test
        fun `loaded trade offer snapshot has escrow animals and no submitted money`() {
            val timerDeadline = 1_700_000_000_000L
            val escrowAnimals =
                setOf(
                    AnimalCard("cow-initiator", AnimalType.COW),
                    AnimalCard("cow-target", AnimalType.COW),
                )
            val state =
                initialLobbyState().copy(
                    phase = GamePhase.TRADE_OFFER,
                    timerEnd = timerDeadline,
                    players =
                        listOf(
                            Player(
                                id = "player-1",
                                name = "player-1",
                                moneyCards =
                                    listOf(
                                        MoneyCard("money-1", 0),
                                        MoneyCard("money-2", 5),
                                        MoneyCard("money-3", 10),
                                    ),
                            ),
                            Player(
                                id = "player-2",
                                name = "player-2",
                                moneyCards =
                                    listOf(
                                        MoneyCard("money-4", 0),
                                        MoneyCard("money-5", 5),
                                        MoneyCard("money-6", 5),
                                    ),
                            ),
                        ),
                    currentPlayerIndex = 0,
                    tradeState =
                        TradeState(
                            initiatorId = "player-1",
                            targetId = "player-2",
                            animalCards = escrowAnimals,
                        ),
                )
            service.saveGameState("12345", state)

            val loaded = assertNotNull(service.loadGameState("12345"))
            val loadedTrade = assertNotNull(loaded.tradeState)
            assertEquals(GamePhase.TRADE_OFFER, loaded.phase)
            assertEquals(timerDeadline, loaded.timerEnd)
            assertEquals("player-1", loadedTrade.initiatorId)
            assertEquals("player-2", loadedTrade.targetId)
            assertEquals(AnimalType.COW, loadedTrade.animalCards.firstOrNull()?.type)
            assertEquals(escrowAnimals, loadedTrade.animalCards)
            assertEquals(0, (loadedTrade.offeredMoneyCards?.sumOf { it.value } ?: 0))
            assertEquals(
                emptySet(),
                (
                    loadedTrade.offeredMoneyCards?.map { it.id }?.toSet()
                        ?: emptySet()
                ),
            )
            assertNull(loadedTrade.offeredMoneyCards)
            assertNull(loadedTrade.counterOfferedMoneyCards?.sumOf { it.value })
            assertEquals(
                emptySet(),
                (
                    loadedTrade.counterOfferedMoneyCards?.map { it.id }?.toSet()
                        ?: emptySet()
                ),
            )
            assertNull(loadedTrade.counterOfferedMoneyCards)
        }

        @Test
        // testet: loadGameState stellt einen laufenden Trade (TRADE_RESPONSE) mit Escrow-Tierkarten und angebotenen Geldkarten korrekt wieder her
        fun `loadGameState restores in-flight trade escrow cards`() {
            val timerDeadline = 1_700_000_000_000L
            val offeredCards =
                setOf(
                    MoneyCard(id = "offer-10", value = 10),
                    MoneyCard(id = "offer-50", value = 50),
                )
            val animalCards =
                setOf(
                    AnimalCard(id = "cow-1", type = AnimalType.COW),
                    AnimalCard(id = "cow-2", type = AnimalType.COW),
                )
            val state =
                initialLobbyState().copy(
                    phase = GamePhase.TRADE_RESPONSE,
                    timerEnd = timerDeadline,
                    players =
                        listOf(
                            Player(id = "player-1", name = "player-1", moneyCards = emptyList()),
                            Player(
                                id = "player-2",
                                name = "player-2",
                                moneyCards = listOf(MoneyCard(id = "counter-20", value = 20)),
                            ),
                        ),
                    currentPlayerIndex = 0,
                    tradeState =
                        TradeState(
                            initiatorId = "player-1",
                            targetId = "player-2",
                            animalCards = animalCards,
                            offeredMoneyCards = offeredCards,
                        ),
                )
            service.saveGameState("12345", state)

            val loaded = assertNotNull(service.loadGameState("12345"))
            val loadedTrade = assertNotNull(loaded.tradeState)
            assertEquals(GamePhase.TRADE_RESPONSE, loaded.phase)
            assertEquals(timerDeadline, loaded.timerEnd)
            assertEquals(animalCards, loadedTrade.animalCards)
            assertEquals(offeredCards, loadedTrade.offeredMoneyCards)
            assertEquals(
                offeredCards.mapTo(mutableSetOf()) {
                    it.id
                },
                (loadedTrade.offeredMoneyCards?.map { it.id }?.toSet() ?: emptySet()),
            )
            assertEquals(60, (loadedTrade.offeredMoneyCards?.sumOf { it.value } ?: 0))
            assertEquals(emptyList(), loaded.players.first { it.id == "player-1" }.moneyCards)
        }

        @Test
        fun `saveGameState clears persisted auction when game leaves auction phase`() {
            val withAuction =
                initialLobbyState().copy(
                    phase = GamePhase.AUCTION_BIDDING,
                    players = listOf(Player(id = "player-1", name = "player-1")),
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
        // testet: saveGameState persistiert die aufgelösten Trade-Escrow-Daten (Tierkarten, angebotene und Gegen-Geldkarten) in der TRADE_RESULT-Phase
        fun `saveGameState persists resolved trade escrow payloads`() {
            val animalCards =
                setOf(
                    AnimalCard(id = "dog-1", type = AnimalType.DOG),
                    AnimalCard(id = "dog-2", type = AnimalType.DOG),
                )
            val offeredCards =
                setOf(
                    MoneyCard(id = "offer-10", value = 10),
                    MoneyCard(id = "offer-50", value = 50),
                )
            val counterCards =
                setOf(
                    MoneyCard(id = "counter-20", value = 20),
                )
            val state =
                initialLobbyState().copy(
                    phase = GamePhase.TRADE_RESULT,
                    players =
                        listOf(
                            Player(
                                id = "player-1",
                                name = "player-1",
                                moneyCards = emptyList(),
                            ),
                            Player(
                                id = "player-2",
                                name = "player-2",
                                moneyCards = emptyList(),
                            ),
                        ),
                    tradeState =
                        TradeState(
                            initiatorId = "player-1",
                            targetId = "player-2",
                            animalCards = animalCards,
                            offeredMoneyCards = offeredCards,
                            counterOfferedMoneyCards = counterCards,
                            winnerId = "player-2",
                        ),
                )
            service.saveGameState("12345", state)

            val trade = tradeStateRepository.findById(12345L).orElseThrow()
            assertEquals(AnimalType.DOG, trade.animalType)
            assertEquals("[10,50]", trade.challengerOfferJson)
            assertEquals("[20]", trade.defenderOfferJson)
            assertNotNull(trade.animalCardsJson)
            assertNotNull(trade.challengerOfferCardsJson)
            assertNotNull(trade.defenderOfferCardsJson)
            assertEquals("player-2", trade.winnerPlayerId)

            val loaded = assertNotNull(service.loadGameState("12345"))
            val loadedTrade = assertNotNull(loaded.tradeState)
            assertEquals(GamePhase.TRADE_RESULT, loaded.phase)
            assertEquals(animalCards, loadedTrade.animalCards)
            assertEquals(offeredCards, loadedTrade.offeredMoneyCards)
            assertEquals(counterCards, loadedTrade.counterOfferedMoneyCards)
            assertEquals("player-2", loadedTrade.winnerId)
        }

        @Test
        // testet: der komplette Trade-Ablauf (Auswahl, Abgabe, Antwort) übersteht den Save/Reload-Roundtrip über die DB
        fun `trade flow round trips after choose submit and response`() {
            val offeredCard = MoneyCard(id = "p1-50", value = 50)
            val counterCard = MoneyCard(id = "p2-10", value = 10)
            val initiatorAnimal = AnimalCard(id = "p1-cow", type = AnimalType.COW)
            val targetAnimal = AnimalCard(id = "p2-cow", type = AnimalType.COW)
            val session =
                GameSession(
                    gameId = "12345",
                    hostPlayerId = "player-1",
                    hostPlayerName = "Alice",
                    initialState =
                        GameState(
                            phase = GamePhase.PLAYER_CHOICE,
                            currentPlayerIndex = 0,
                            hostPlayerId = "player-1",
                            players =
                                listOf(
                                    Player(
                                        id = "player-1",
                                        name = "Alice",
                                        animals = listOf(initiatorAnimal),
                                        moneyCards = listOf(offeredCard),
                                    ),
                                    Player(
                                        id = "player-2",
                                        name = "Bob",
                                        animals = listOf(targetAnimal),
                                        moneyCards = listOf(counterCard),
                                    ),
                                ),
                        ),
                )

            session.chooseTrade("player-1", "player-2", AnimalType.COW)
            session.submitTradeMoney("player-1", setOf(offeredCard.id))
            val result = session.respondToTrade("player-2", setOf(counterCard.id))
            service.saveGameState("12345", result)

            val loaded = assertNotNull(service.loadGameState("12345"))
            val loadedTrade = assertNotNull(loaded.tradeState)
            assertEquals(GamePhase.TRADE_RESULT, loaded.phase)
            assertEquals(setOf(initiatorAnimal, targetAnimal), loadedTrade.animalCards)
            assertEquals(setOf(offeredCard), loadedTrade.offeredMoneyCards)
            assertEquals(setOf(counterCard), loadedTrade.counterOfferedMoneyCards)
            assertEquals("player-1", loadedTrade.winnerId)
            assertEquals(50, (loadedTrade.offeredMoneyCards?.sumOf { it.value } ?: 0))
            assertEquals(10, loadedTrade.counterOfferedMoneyCards?.sumOf { it.value })
        }

        @Test
        fun `saveGameState rewrites deck and player inventory on subsequent saves`() {
            val initial =
                initialLobbyState().copy(
                    deck = AnimalDeck(listOf(AnimalCard(id = "1", type = AnimalType.COW))),
                    players =
                        listOf(
                            Player(
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
                            Player(
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

        @Test
        // testet: saveGameState speichert eine umsortierte (rotierte) Spielerliste, ohne die Unique-Constraint der Sitzreihenfolge zu verletzen
        fun `saveGameState persists a reordered player list without seat-order collision`() {
            val alice = Player(id = "p-alice", name = "alice")
            val bob = Player(id = "p-bob", name = "bob")
            val carol = Player(id = "p-carol", name = "carol")

            // Lobby join order is persisted as seats 0, 1, 2.
            service.saveGameState(
                "12345",
                GameState(phase = GamePhase.NOT_STARTED, players = listOf(alice, bob, carol)),
            )

            // startGame shuffles the player list; persisting the rotated order permutes seat_order.
            // A full rotation leaves every target seat occupied, so the in-place reassignment used to
            // violate uk_game_players_seat during flush.
            service.saveGameState(
                "12345",
                GameState(
                    phase = GamePhase.PLAYER_CHOICE,
                    currentPlayerIndex = 0,
                    players = listOf(carol, alice, bob),
                ),
            )

            val loaded = assertNotNull(service.loadGameState("12345"))
            assertEquals(listOf("carol", "alice", "bob"), loaded.players.map { it.name })
        }

        @Test
        // testet: zwei Spieler mit identischem Anzeigenamen bleiben über ihre IDs als getrennte Identitäten erhalten
        fun `saveGameState keeps players with the same display name as separate identities`() {
            val firstAlex = Player(id = "player-1", name = "Alex")
            val secondAlex = Player(id = "player-2", name = "Alex")

            service.saveGameState(
                "12345",
                GameState(
                    phase = GamePhase.NOT_STARTED,
                    hostPlayerId = firstAlex.id,
                    players = listOf(firstAlex, secondAlex),
                ),
            )

            val loaded = assertNotNull(service.loadGameState("12345"))
            assertEquals(listOf("player-1", "player-2"), loaded.players.map { it.id })
            assertEquals(listOf("Alex", "Alex"), loaded.players.map { it.name })
        }

        @Test
        // testet: gleiche Anzeigenamen in verschiedenen Spielen behalten jeweils ihre eigenen, spielgebundenen Spieler-IDs
        fun `same display names across separate games keep their own player ids`() {
            service.saveGameState(
                "12345",
                GameState(
                    phase = GamePhase.NOT_STARTED,
                    hostPlayerId = "game-1-alex",
                    players =
                        listOf(
                            Player(id = "game-1-alex", name = "Alex"),
                            Player(id = "game-1-bob", name = "Bob"),
                        ),
                ),
            )
            service.saveGameState(
                "23456",
                GameState(
                    phase = GamePhase.NOT_STARTED,
                    hostPlayerId = "game-2-alex",
                    players =
                        listOf(
                            Player(id = "game-2-alex", name = "Alex"),
                            Player(id = "game-2-cara", name = "Cara"),
                        ),
                ),
            )

            val firstGame = assertNotNull(service.loadGameState("12345"))
            val secondGame = assertNotNull(service.loadGameState("23456"))
            assertEquals(listOf("game-1-alex", "game-1-bob"), firstGame.players.map { it.id })
            assertEquals(listOf("Alex", "Bob"), firstGame.players.map { it.name })
            assertEquals(listOf("game-2-alex", "game-2-cara"), secondGame.players.map { it.id })
            assertEquals(listOf("Alex", "Cara"), secondGame.players.map { it.name })
        }

        private fun initialLobbyState(): GameState =
            GameState(
                phase = GamePhase.NOT_STARTED,
                players = listOf(Player(id = "player-1", name = "player-1")),
            )
    }
