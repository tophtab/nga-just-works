package com.justwen.androidnga.core.data.thread

/** Raw post/comment fields and per-decode user association. No HTML, blacklist or presentation state. */
data class ReadPostWire(
    val tid: ReadField<Int>,
    val fid: ReadField<Int>,
    val authorId: ReadField<Int>,
    val pid: ReadField<Int>,
    val lou: ReadField<Int>,
    val subject: ReadField<String>,
    val content: ReadField<String>,
    val alterInfo: ReadField<String>,
    val vote: ReadField<String>,
    val postDate: ReadField<String>,
    val level: ReadField<String>,
    val fromClient: ReadField<String>,
    val score: ReadField<Int>,
    val author: ReadField<String>,
    val anonymous: ReadField<Boolean>,
    val yz: ReadField<String>,
    val avatar: ReadField<String>,
    val muteTime: ReadField<String>,
    val aurvrc: ReadField<Int>,
    val signature: ReadField<String>,
    val muted: ReadField<Boolean>,
    val postCount: ReadField<String>,
    val reputation: ReadField<Float>,
    val memberGroup: ReadField<String>,
    val attachments: ReadField<Map<String, ReadField<ReadAttachmentWire>>>,
    val comments: ReadField<List<ReadPostWire>>,
    val hotReplyIds: ReadField<String>,
    val user: ReadField<ReadUserWire>,
    // Scoped bean projection after removal of only invalid canonical source keys.
    val scopedSource: ReadScopedSourceWire,
    // The original floor-presence test examines literal "lou", independently of bean aliases.
    val floorPresent: Boolean,
    // Structural lookup coordinate for app-owned bean compatibility state, never raw response data.
    val sourcePath: String,
)

/** Effective source text from the existing scoped projection; raw validity stays in the post fields. */
data class ReadScopedSourceWire(val content: String?, val subject: String?, val alterInfo: String?)
