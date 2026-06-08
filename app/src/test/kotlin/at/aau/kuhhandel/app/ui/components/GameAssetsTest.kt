package at.aau.kuhhandel.app.ui.components

import at.aau.kuhhandel.app.R
import kotlin.test.Test
import kotlin.test.assertEquals

class GameAssetsTest {
    @Test
    fun `table offer drawable caps artwork at seven cards`() {
        assertEquals(
            R.drawable.ig_money_hidden_table_7,
            getHiddenMoneyTableDrawable(count = 12),
        )
    }

    @Test
    fun `counter offer uses mirrored table drawable`() {
        assertEquals(
            R.drawable.ig_money_hidden_table_counter_4,
            getHiddenMoneyTableDrawable(
                count = 4,
                isCounterOffer = true,
            ),
        )
    }
}
