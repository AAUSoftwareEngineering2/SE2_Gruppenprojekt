package at.aau.kuhhandel.server.persistence.mapper

import at.aau.kuhhandel.shared.enums.GamePhase
import org.junit.jupiter.api.Test
import at.aau.kuhhandel.server.persistence.entity.GameStatus as PersistedStatus

class GameStateMapperTest {
    @Test
    fun `auction phase maps to AUCTION status`() {
        kotlin.test.assertEquals(
            PersistedStatus.AUCTION,
            GameStateMapper.toGameStatus(GamePhase.AUCTION),
        )
    }

    @Test
    fun `trade phase maps to TRADE status`() {
        kotlin.test.assertEquals(
            PersistedStatus.TRADE,
            GameStateMapper.toGameStatus(GamePhase.TRADE),
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
    fun `not started turn and round end all collapse to LOBBY`() {
        kotlin.test.assertEquals(
            PersistedStatus.LOBBY,
            GameStateMapper.toGameStatus(GamePhase.NOT_STARTED),
        )
        kotlin.test.assertEquals(
            PersistedStatus.LOBBY,
            GameStateMapper.toGameStatus(GamePhase.PLAYER_TURN),
        )
        kotlin.test.assertEquals(
            PersistedStatus.LOBBY,
            GameStateMapper.toGameStatus(GamePhase.ROUND_END),
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
