package com.justwen.androidnga.core.data.thread

/** One already-unwrapped normal-read data object; total rows never drive the current-row traversal. */
data class ReadThreadWire(
    val topic: ReadField<ReadTopicWire>,
    // Owner lookup is independent of optional topic bean conversion.
    val topicAuthorId: ReadField<Int>,
    val users: ReadField<Map<String, ReadField<ReadUserWire>>>,
    val groups: ReadField<Map<String, ReadField<String>>>,
    val rows: ReadField<List<ReadPostWire>>,
    val currentRowCount: ReadField<Int>,
    val totalRowCount: ReadField<Int>,
    val global: ReadField<Unit>,
    val attachmentBaseView: ReadField<String>,
    val page: ReadField<Int>,
    val rowsPerPage: ReadField<Int>,
)
