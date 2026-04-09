package at.aau.kuhhandel.shared.enums

enum class AnimalType (val points: Int) {
    CHICKEN(10),
    DUCK(40),
    CAT(90),
    DOG(160),
    SHEEP(250), // AnimalTypes have their points already assigned
    GOAT(350),
    DONKEY(500),
    PIG(650),
    COW(800),
    HORSE(1000)
}
