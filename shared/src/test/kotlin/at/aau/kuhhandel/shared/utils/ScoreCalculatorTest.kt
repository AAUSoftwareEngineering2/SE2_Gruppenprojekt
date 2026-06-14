package at.aau.kuhhandel.shared.utils

import at.aau.kuhhandel.shared.enums.AnimalType
import at.aau.kuhhandel.shared.model.AnimalCard
import at.aau.kuhhandel.shared.model.MoneyCard
import at.aau.kuhhandel.shared.model.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScoreCalculatorTest {
    @Test
    fun `calculateScore returns zero for player without quartets`() {
        val player = Player("1", "P1", animals = listOf(AnimalCard("1", AnimalType.COW)))
        assertEquals(0, ScoreCalculator.calculateScore(player))
    }

    @Test
    fun `calculateScore calculates correctly for single quartet`() {
        val animals = (1..4).map { AnimalCard("cow-$it", AnimalType.COW) }
        val player = Player("1", "P1", animals = animals)
        // Cow = 800. 1 quartet. 800 * 1 = 800.
        assertEquals(800, ScoreCalculator.calculateScore(player))
    }

    @Test
    fun `calculateScore calculates correctly for multiple quartets`() {
        val cows = (1..4).map { AnimalCard("cow-$it", AnimalType.COW) }
        val pigs = (1..4).map { AnimalCard("pig-$it", AnimalType.PIG) }
        val player = Player("1", "P1", animals = cows + pigs)
        // Cow = 800, Pig = 650. Sum = 1450. 2 quartets. 1450 * 2 = 2900.
        assertEquals(2900, ScoreCalculator.calculateScore(player))
    }

    @Test
    fun `calculateGameRanking sorts by points descending`() {
        val p1 =
            Player(
                "1",
                "P1",
                animals =
                    (1..4).map
                        { AnimalCard("cow-$it", AnimalType.COW) },
            ) // 800
        val p2 =
            Player(
                "2",
                "P2",
                animals =
                    (1..4).map
                        { AnimalCard("horse-$it", AnimalType.HORSE) },
            ) // 1000

        val ranking = ScoreCalculator.calculateGameRanking(listOf(p1, p2))

        assertEquals("2", ranking[0].playerId)
        assertEquals(1000, ranking[0].points)
        assertEquals("1", ranking[1].playerId)
        assertEquals(800, ranking[1].points)
        assertTrue(ranking[0].isWinner)
    }

    @Test
    fun `calculateGameRanking uses money as tie-breaker`() {
        val p1 =
            Player(
                "1",
                "P1",
                animals = (1..4).map { AnimalCard("cow-$it", AnimalType.COW) },
                moneyCards = listOf(MoneyCard("m1", 10)),
            ) // 800 pts, 10 money
        val p2 =
            Player(
                "2",
                "P2",
                animals = (1..4).map { AnimalCard("cow-$it", AnimalType.COW) },
                moneyCards = listOf(MoneyCard("m2", 50)),
            ) // 800 pts, 50 money

        val ranking = ScoreCalculator.calculateGameRanking(listOf(p1, p2))

        assertEquals("2", ranking[0].playerId)
        assertEquals(50, ranking[0].totalMoney)
        assertEquals("1", ranking[1].playerId)
        assertEquals(10, ranking[1].totalMoney)
    }
}
