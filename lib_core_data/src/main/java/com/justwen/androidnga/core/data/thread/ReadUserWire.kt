package com.justwen.androidnga.core.data.thread

/** User-table data and the matching group label; anonymous names/reputation remain app projection policy. */
data class ReadUserWire(
    val username: ReadField<String>,
    val avatar: ReadField<String>,
    val yz: ReadField<String>,
    val muteTime: ReadField<String>,
    val rvrc: ReadField<String>,
    val signature: ReadField<String>,
    val postCount: ReadField<String>,
    val memberId: ReadField<String>,
    val buffIds: ReadField<List<String>>,
    val groupName: ReadField<String>,
    // Empty object clears the label; absent/null object leaves the row fallback unchanged.
    val groupResolved: Boolean,
)
