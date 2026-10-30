package com.justwen.androidnga.module.message

/**
 * Wrapper and token repairs inherited from the retired message parsing factory.
 *
 * NGA answers private-message calls with a JavaScript assignment, may append an HTML
 * error-fill comment, and sometimes emits `content`/`subject` values that are not valid
 * JSON tokens. The repairs below are byte-for-byte the old factory behavior; they are
 * kept as one shared implementation so the list, detail and send parsers cannot drift.
 */
internal object LegacyMessageJson {

    private const val SCRIPT_PREFIX = "window.script_muti_get_var_store="
    private const val ERROR_FILL_MARKER = "/*error fill content"
    private const val JS_MARKER = "/*\$js\$*/"

    private val ContentPlusToken = Regex("\"content\":\\+(\\d+),")
    private val SubjectPlusToken = Regex("\"subject\":\\+(\\d+),")
    private val ContentLeadingZeroToken = Regex("\"content\":(0\\d+),")
    private val SubjectLeadingZeroToken = Regex("\"subject\":(0\\d+),")
    private val AuthorLeadingZeroToken = Regex("\"author\":(0\\d+),")

    /** Removes the script assignment prefix and the empty JS marker comment. */
    fun stripWrapper(raw: String): String =
        raw.replace(SCRIPT_PREFIX, "").replace(JS_MARKER, "")

    /**
     * Cuts everything from the error-fill comment on, matching the old factory: that
     * comment is not JSON and was never part of the payload.
     */
    fun truncateErrorFill(text: String): String {
        val index = text.indexOf(ERROR_FILL_MARKER)
        return if (index > 0) text.substring(0, index) else text
    }

    /**
     * Quotes numeric-looking `content`/`subject` tokens that the server sends as
     * `+123` or `0123`. Detail pages also repair `author`.
     */
    fun repairNumericTokens(text: String, includeAuthor: Boolean = false): String {
        var repaired = text
            .replace(ContentPlusToken) { "\"content\":\"+${it.groupValues[1]}\"," }
            .replace(SubjectPlusToken) { "\"subject\":\"+${it.groupValues[1]}\"," }
            .replace(ContentLeadingZeroToken) { "\"content\":\"${it.groupValues[1]}\"," }
            .replace(SubjectLeadingZeroToken) { "\"subject\":\"${it.groupValues[1]}\"," }
        if (includeAuthor) {
            repaired = repaired.replace(AuthorLeadingZeroToken) {
                "\"author\":\"${it.groupValues[1]}\","
            }
        }
        return repaired
    }

    /** The full list/send repair chain: strip wrapper, cut error fill, repair tokens. */
    fun clean(raw: String): String =
        repairNumericTokens(truncateErrorFill(stripWrapper(raw)))
}
