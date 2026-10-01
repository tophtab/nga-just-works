package com.justwen.androidnga.core.data.thread

/** Complete attachment protocol data. Map keys and iteration order live in the containing post. */
data class ReadAttachmentWire(
    val aid: ReadField<String>,
    val urlUtf8OrgName: ReadField<String>,
    val dscp: ReadField<String>,
    val size: ReadField<Int>,
    val ext: ReadField<String>,
    val name: ReadField<String>,
    val thumb: ReadField<String>,
    val attachUrl: ReadField<String>,
    val type: ReadField<String>,
    val subid: ReadField<Int>,
)
