package at.aau.kuhhandel.shared.model

import org.junit.Test
import kotlin.test.assertEquals

class PlayerStateTest {
    @Test
    fun test_totalMoney() {
        val player =
            PlayerState(
                id = "1",
                name = "Test",
                moneyCards =
                    listOf(
                        MoneyCard("1", 10),
                        MoneyCard("2", 50),
                    ),
            )

        assertEquals(60, player.totalMoney())
    }
}
