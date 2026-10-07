package sp.phone.mvp.model.convert

import com.justwen.androidnga.core.data.thread.ReadAttachmentWire
import com.justwen.androidnga.core.data.thread.ReadPostWire
import com.justwen.androidnga.core.data.thread.ReadThreadWire
import com.justwen.androidnga.core.data.thread.ReadTopicWire
import gov.anzong.androidnga.common.util.NgaImageHost
import sp.phone.common.ForumConstants
import sp.phone.http.bean.Attachment
import sp.phone.http.bean.ThreadData
import sp.phone.http.bean.ThreadRowInfo
import sp.phone.mvp.model.entity.ThreadPageInfo
import sp.phone.mvp.model.thread.ArticleAuthorSupport
import sp.phone.mvp.model.thread.ArticleBlacklist
import sp.phone.mvp.model.thread.ArticleRowKind
import sp.phone.mvp.model.thread.ArticleRowPresentation
import sp.phone.mvp.model.thread.ArticleRowRenderer
import sp.phone.util.StringUtils

/** Adapts protocol data to the retained Java models; all display policy stays in the app. */
internal object ReadThreadLegacyMapper {
    @JvmStatic
    fun map(wire: ReadThreadWire, raw: String, strict: Boolean,
            renderer: ArticleRowRenderer, blacklist: ArticleBlacklist, fallbacks: ReadThreadBeanFallbacks): ThreadData {
        val prefix = NgaImageHost.attachmentsPrefix(wire.attachmentBaseView.value)
        val rows = wire.rows.value.orEmpty().mapTo(ArrayList()) {
            post(it, wire.topicAuthorId.value, false, strict, prefix, renderer, blacklist, fallbacks,
                wire.topic.value?.subject?.value)
        }
        return ThreadData().apply {
            rawData = raw
            threadInfo = wire.topic.value?.let(::topic)
            rowList = rows
            set__ROWS(wire.totalRowCount.value!!)
            rowNum = rows.size
            if (strict) isContentComplete = complete(rows)
        }
    }

    private fun post(wire: ReadPostWire, owner: Int?, comment: Boolean, strict: Boolean,
                     prefix: String, renderer: ArticleRowRenderer, blacklist: ArticleBlacklist,
                     fallbacks: ReadThreadBeanFallbacks, topicSubject: String? = null): ThreadRowInfo {
        val sourceSubject = if (strict) wire.scopedSource.subject else wire.subject.value
        val row = fallbacks.row(wire.sourcePath).apply {
            tid = wire.tid.value ?: 0
            fid = wire.fid.value ?: 0
            authorid = wire.authorId.value ?: 0
            pid = wire.pid.value ?: 0
            lou = wire.lou.value ?: 0
            subject = sourceSubject
            content = if (strict) wire.scopedSource.content else wire.content.value
            alterinfo = if (strict) wire.scopedSource.alterInfo else wire.alterInfo.value
            vote = wire.vote.value
            postdate = wire.postDate.value
            level = wire.level.value
            score = wire.score.value ?: 0
            author = wire.author.value
            isanonymous = wire.anonymous.value ?: false
            yz = wire.yz.value
            js_escap_avatar = wire.avatar.value
            muteTime = wire.muteTime.value
            aurvrc = wire.aurvrc.value ?: 0
            signature = wire.signature.value
            isMuted = wire.muted.value ?: false
            postCount = wire.postCount.value
            reputation = wire.reputation.value ?: 0f
            memberGroup = wire.memberGroup.value
            attachs = wire.attachments.value?.mapValuesTo(HashMap()) { (_, value) -> value.value?.let(::attachment) }
            if (wire.hotReplyIds.value != null) hotReplies = wire.hotReplyIds.value!!.split(',').filterTo(ArrayList()) { it.isNotEmpty() }
        }
        if (wire.comments.value != null) row.comments = wire.comments.value!!.mapTo(ArrayList()) { post(it, owner, true, strict, prefix, renderer, blacklist, fallbacks) }
        row.fromClient = wire.fromClient.value
        row.fromClientModel = ArticleAuthorSupport.clientModel(row.fromClient)
        applyUser(row, wire, blacklist)
        val sourceAvailable = !strict || wire.content.isSourceScalar && wire.subject.isSourceScalar &&
            (row.content != null || row.subject != null || !row.alterinfo.isNullOrEmpty())
        val isOwner = if (row.authorid > 0 && !row.isanonymous && owner != null && owner > 0) row.authorid == owner else null
        row.presentation = ArticleRowPresentation(
            if (comment) ArticleRowKind.COMMENT else ArticleRowKind.POST,
            wire.floorPresent && row.lou >= 0,
            row.authorid > 0 && !row.isanonymous, true, sourceAvailable, isOwner,
        )
        if (row.content == null) {
            row.content = row.subject
            row.subject = null
        }
        // Only a known main post inherits the topic title; preserve subject-as-body handling.
        if (!comment && wire.floorPresent && row.lou == 0 && sourceSubject.isNullOrEmpty()
            && !topicSubject.isNullOrEmpty()) {
            row.subject = topicSubject
        }
        if (row.fromClient?.startsWith("103 ") == true && !row.content.isNullOrEmpty()) {
            row.content = StringUtils.unescape(row.content)
        }
        // The old bean may leave an invalid raw map entry; production rendering rejects its cast.
        // Keep that failure after comments/user processing without leaking raw JSON across core.
        if (wire.attachments.value?.values?.any { !it.valid } == true) {
            throw IllegalArgumentException("Unrenderable attachment")
        }
        renderer.render(row, prefix)
        return row
    }

    private fun applyUser(row: ThreadRowInfo, wire: ReadPostWire, blacklist: ArticleBlacklist) {
        val user = wire.user.value ?: return
        row.set_IsInBlackList(blacklist.contains(row.authorid.toString()))
        val username = user.username.value
        if (username != null && username.length == 39 && username.startsWith("#anony_")) {
            row.author = ArticleAuthorSupport.anonymousName(username)
            row.isanonymous = true
        } else row.author = username
        row.js_escap_avatar = user.avatar.value
        row.yz = user.yz.value
        row.muteTime = user.muteTime.value
        val reputationText = user.rvrc.value
        row.aurvrc = try { reputationText?.let(Integer::valueOf) ?: 0 } catch (_: NumberFormatException) { 0 }
        row.signature = user.signature.value
        // Keep partial updates: postCount survives a failed reputation parse; group comes last.
        row.postCount = user.postCount.value
        if (reputationText != null) {
            try {
                row.reputation = java.lang.Float.parseFloat(reputationText) / 10.0f
                if (user.groupName.valid && user.groupResolved) row.memberGroup = user.groupName.value
            } catch (_: NumberFormatException) { }
        }
        if (user.buffIds.value?.any { it in ForumConstants.BUFF_MUTE_IDS } == true) row.isMuted = true
    }

    private fun complete(rows: List<ThreadRowInfo>): Boolean = rows.all {
        it.presentation.sourceAvailable && (it.comments == null || complete(it.comments))
    }

    private fun attachment(wire: ReadAttachmentWire) = Attachment().apply {
        aid = wire.aid.value
        url_utf8_org_name = wire.urlUtf8OrgName.value
        dscp = wire.dscp.value
        size = wire.size.value ?: 0
        ext = wire.ext.value
        name = wire.name.value
        thumb = wire.thumb.value
        attachurl = wire.attachUrl.value
        type = wire.type.value
        subid = wire.subid.value ?: 0
    }

    private fun topic(wire: ReadTopicWire) = ThreadPageInfo().apply {
        tid = wire.tid.value ?: 0
        fid = wire.fid.value ?: 0
        author = wire.author.value
        authorId = wire.authorId.value ?: 0
        lastPoster = wire.lastPoster.value
        replies = wire.replies.value ?: 0
        subject = wire.subject.value
        titleFont = wire.titleFont.value
        type = wire.type.value ?: 0
        topicMisc = wire.topicMisc.value
        postDate = wire.postDate.value ?: 0
        page = wire.page.value ?: 0
        pid = wire.pid.value ?: 0
        position = wire.position.value ?: 0
        isAnonymity = wire.anonymity.value ?: false
        board = wire.board.value
        replyInfo = wire.replyInfo.value?.let { reply ->
            ThreadPageInfo.ReplyInfo().apply {
                pidStr = reply.pidStr.value
                tidStr = reply.tidStr.value
                authorId = reply.authorId.value
                content = reply.content.value
                subject = reply.subject.value
                postDate = reply.postDate.value
            }
        }
    }
}
