package com.justwen.androidnga.core.data.thread

/** Original JSON shape, including absence; no JSON implementation types cross this boundary. */
enum class ReadValueKind { MISSING, NULL, STRING, NUMBER, BOOLEAN, OBJECT, ARRAY, OTHER }

/**
 * [value] is the decoded protocol value. An invalid conversion is retained separately
 * from a missing/null field; callers must not mistake it for an ordinary default.
 * Text values keep legacy string coercion, while [isSourceScalar] controls scoped source use.
 */
data class ReadField<out T>(
    val kind: ReadValueKind,
    val value: T?,
    val valid: Boolean = true,
) {
    val present: Boolean get() = kind != ReadValueKind.MISSING
    val isSourceScalar: Boolean get() = kind == ReadValueKind.MISSING ||
        kind == ReadValueKind.NULL || kind == ReadValueKind.STRING || kind == ReadValueKind.NUMBER
}
