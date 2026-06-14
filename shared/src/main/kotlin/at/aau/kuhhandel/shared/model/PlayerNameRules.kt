package at.aau.kuhhandel.shared.model

/**
 * Validation rules for user-chosen player display names.
 *
 * Shared between client (live input feedback) and server (defense-in-depth).
 */
object PlayerNameRules {
    const val MIN_LENGTH: Int = 1
    const val MAX_LENGTH: Int = 8

    private val ALLOWED_PATTERN = Regex("^[A-Za-z0-9]+$")

    fun isValid(name: String?): Boolean = validate(name) == null

    /**
     * Returns `null` if [name] is valid, otherwise the reason it failed.
     */
    fun validate(name: String?): Violation? {
        if (name.isNullOrEmpty()) return Violation.EMPTY
        if (name.length > MAX_LENGTH) return Violation.TOO_LONG
        if (!ALLOWED_PATTERN.matches(name)) return Violation.INVALID_CHARACTERS
        return null
    }

    enum class Violation {
        EMPTY,
        TOO_LONG,
        INVALID_CHARACTERS,
    }
}
