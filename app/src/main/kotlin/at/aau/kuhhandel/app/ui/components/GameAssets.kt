package at.aau.kuhhandel.app.ui.components

import at.aau.kuhhandel.app.R
import at.aau.kuhhandel.shared.enums.AnimalType

/** Defines the visual style of an animal representation. */
enum class AnimalStyle {
    CHIP,
    CARD,
}

/** Maps an [AnimalType] and [AnimalStyle] to its corresponding drawable resource ID. */
fun getAnimalDrawable(
    type: AnimalType,
    style: AnimalStyle = AnimalStyle.CHIP,
): Int =
    when (style) {
        AnimalStyle.CHIP ->
            when (type) {
                AnimalType.CHICKEN -> R.drawable.hs_chicken
                AnimalType.GOOSE -> R.drawable.hs_goose
                AnimalType.CAT -> R.drawable.hs_cat
                AnimalType.DOG -> R.drawable.hs_dog
                AnimalType.SHEEP -> R.drawable.hs_sheep
                AnimalType.GOAT -> R.drawable.hs_goat
                AnimalType.DONKEY -> R.drawable.hs_donkey
                AnimalType.PIG -> R.drawable.hs_pig
                AnimalType.COW -> R.drawable.hs_cow
                AnimalType.HORSE -> R.drawable.hs_horse
            }
        AnimalStyle.CARD ->
            when (type) {
                AnimalType.CHICKEN -> R.drawable.auc_chicken
                AnimalType.GOOSE -> R.drawable.auc_goose
                AnimalType.CAT -> R.drawable.auc_cat
                AnimalType.DOG -> R.drawable.auc_dog
                AnimalType.SHEEP -> R.drawable.auc_sheep
                AnimalType.GOAT -> R.drawable.auc_goat
                AnimalType.DONKEY -> R.drawable.auc_donkey
                AnimalType.PIG -> R.drawable.auc_pig
                AnimalType.COW -> R.drawable.auc_cow
                AnimalType.HORSE -> R.drawable.auc_horse
            }
    }

/** Maps a money card value to its corresponding revealed drawable resource ID. */
fun getMoneyDrawable(value: Int): Int =
    when (value) {
        0 -> R.drawable.ig_money_revealed_0
        10 -> R.drawable.ig_money_revealed_10
        50 -> R.drawable.ig_money_revealed_50
        100 -> R.drawable.ig_money_revealed_100
        200 -> R.drawable.ig_money_revealed_200
        500 -> R.drawable.ig_money_revealed_500
        else -> R.drawable.ig_money_revealed_0
    }

/** Maps a card count to the corresponding hidden stack drawable resource ID. */
fun getHiddenMoneyStackDrawable(count: Int): Int =
    when (count.coerceIn(1, 7)) {
        1 -> R.drawable.ig_money_hidden_large_1
        2 -> R.drawable.ig_money_hidden_large_2
        3 -> R.drawable.ig_money_hidden_large_3
        4 -> R.drawable.ig_money_hidden_large_4
        5 -> R.drawable.ig_money_hidden_large_5
        6 -> R.drawable.ig_money_hidden_large_6
        7 -> R.drawable.ig_money_hidden_large_7
        else -> R.drawable.ig_money_hidden_large_7
    }

/** Maps a card count to the corresponding diagonal hidden stack drawable resource ID. */
fun getHiddenMoneyDiagonalDrawable(count: Int): Int =
    when (count.coerceIn(1, 7)) {
        1 -> R.drawable.ig_money_hidden_diagonal_1
        2 -> R.drawable.ig_money_hidden_diagonal_2
        3 -> R.drawable.ig_money_hidden_diagonal_3
        4 -> R.drawable.ig_money_hidden_diagonal_4
        5 -> R.drawable.ig_money_hidden_diagonal_5
        6 -> R.drawable.ig_money_hidden_diagonal_6
        7 -> R.drawable.ig_money_hidden_diagonal_7
        else -> R.drawable.ig_money_hidden_diagonal_7
    }

/** Maps a submitted trade card count to its table stack drawable. */
fun getHiddenMoneyTableDrawable(
    count: Int,
    isCounterOffer: Boolean = false,
): Int {
    val visibleCardCount = count.coerceIn(1, 7)

    return if (isCounterOffer) {
        when (visibleCardCount) {
            1 -> R.drawable.ig_money_hidden_table_counter_1
            2 -> R.drawable.ig_money_hidden_table_counter_2
            3 -> R.drawable.ig_money_hidden_table_counter_3
            4 -> R.drawable.ig_money_hidden_table_counter_4
            5 -> R.drawable.ig_money_hidden_table_counter_5
            6 -> R.drawable.ig_money_hidden_table_counter_6
            7 -> R.drawable.ig_money_hidden_table_counter_7
            else -> R.drawable.ig_money_hidden_table_counter_7
        }
    } else {
        when (visibleCardCount) {
            1 -> R.drawable.ig_money_hidden_table_1
            2 -> R.drawable.ig_money_hidden_table_2
            3 -> R.drawable.ig_money_hidden_table_3
            4 -> R.drawable.ig_money_hidden_table_4
            5 -> R.drawable.ig_money_hidden_table_5
            6 -> R.drawable.ig_money_hidden_table_6
            7 -> R.drawable.ig_money_hidden_table_7
            else -> R.drawable.ig_money_hidden_table_7
        }
    }
}
