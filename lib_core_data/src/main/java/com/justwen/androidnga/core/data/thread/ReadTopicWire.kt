package com.justwen.androidnga.core.data.thread

/** Optional topic metadata; invalid conversion is represented by the containing ReadField. */
data class ReadTopicWire(
    val tid: ReadField<Int>,
    val fid: ReadField<Int>,
    val author: ReadField<String>,
    val authorId: ReadField<Int>,
    val lastPoster: ReadField<String>,
    val replies: ReadField<Int>,
    val subject: ReadField<String>,
    val titleFont: ReadField<String>,
    val type: ReadField<Int>,
    val topicMisc: ReadField<String>,
    val postDate: ReadField<Int>,
    val page: ReadField<Int>,
    val pid: ReadField<Int>,
    val position: ReadField<Int>,
    val anonymity: ReadField<Boolean>,
    val replyInfo: ReadField<ReadTopicReplyWire>,
    val board: ReadField<String>,
)

/** Persisted navigation reply metadata accepted by the existing topic bean. */
data class ReadTopicReplyWire(
    val pidStr: ReadField<String>,
    val tidStr: ReadField<String>,
    val authorId: ReadField<String>,
    val content: ReadField<String>,
    val subject: ReadField<String>,
    val postDate: ReadField<String>,
)
