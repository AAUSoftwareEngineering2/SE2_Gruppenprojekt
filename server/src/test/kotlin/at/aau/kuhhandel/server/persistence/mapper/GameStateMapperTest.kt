package at.aau.kuhhandel.server.persistence.mapper

import at.aau.kuhhandel.shared.enums.GamePhase
import org.junit.jupiter.api.Test
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
            GameStateMapper.toGameStatus(GamePhase.AUCTION_RESOLUTION),
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
            GameStateMapper.toGameStatus(GamePhase.TRADE_REVEAL),
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
}
