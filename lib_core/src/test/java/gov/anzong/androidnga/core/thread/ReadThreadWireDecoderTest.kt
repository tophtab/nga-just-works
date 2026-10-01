package gov.anzong.androidnga.core.thread

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.justwen.androidnga.core.data.thread.*
import org.junit.Assert.*
import org.junit.Test

class ReadThreadWireDecoderTest {
    private fun data(rows: String = """{"0":{"tid":120002,"pid":51,"authorid":42,"content":"body"}}""", count: Int = 1) =
        JSON.parseObject("""{"__ROWS":900,"__R__ROWS":$count,"__R":$rows}""")
    private fun success(obj: JSONObject, mode: ReadDecodeMode = ReadDecodeMode.SCOPED): ReadThreadWire {
        val result = ReadThreadWireDecoder.decode(obj, mode)
        assertTrue(result.toString(), result is ReadThreadDecodeResult.Success)
        return (result as ReadThreadDecodeResult.Success).thread
    }
    private fun failure(obj: JSONObject, problem: ReadShapeProblem, mode: ReadDecodeMode = ReadDecodeMode.SCOPED) {
        val result = ReadThreadWireDecoder.decode(obj, mode)
        assertTrue(result.toString(), result is ReadThreadDecodeResult.Failure)
        assertEquals(problem, (result as ReadThreadDecodeResult.Failure).problem)
    }
    private fun ReadThreadWire.row() = rows.value!!.single()

    @Test fun decodesEveryPostAndAttachmentFieldWithoutApplyingDisplayPolicy() {
        val obj = data("""{"0":{
            "tid":120002,"fid":-7,"authorid":42,"pid":50120,"lou":120,
            "subject":"subject","content":"%u4E2D%u6587","alterinfo":"notice","vote":"vote wire",
            "postdate":1700000000,"level":"level","from_client":"103 device OS detail","score":5,
            "author":"row author","isanonymous":true,"yz":"-1","js_escap_avatar":"row-avatar",
            "muteTime":"row-mute","aurvrc":23,"signature":"row signature","muted":true,
            "postCount":"row count","reputation":2.3,"memberGroup":"row group","17":"51,,52,51,",
            "attachs":{"second":{"aid":"A","url_utf8_org_name":"原名.mp4","dscp":"description","size":12345,
              "ext":"mp4","name":"file","thumb":"1","attachurl":"mon_test/a.mp4","type":"video","subid":7},
              "first":{"attachurl":"mon_test/b.jpg"}},
            "formattedHtmlData":"untrusted","imageUrls":["untrusted"],"presentation":{"sourceAvailable":true}
        }}""")
        val row = success(obj).row()
        assertEquals(listOf(120002,-7,42,50120,120,5,23), listOf(row.tid.value,row.fid.value,row.authorId.value,
            row.pid.value,row.lou.value,row.score.value,row.aurvrc.value))
        assertEquals(listOf("subject","%u4E2D%u6587","notice","vote wire","1700000000","level","103 device OS detail"),
            listOf(row.subject.value,row.content.value,row.alterInfo.value,row.vote.value,row.postDate.value,row.level.value,row.fromClient.value))
        assertEquals(listOf("row author","-1","row-avatar","row-mute","row signature","row count","row group","51,,52,51,"),
            listOf(row.author.value,row.yz.value,row.avatar.value,row.muteTime.value,row.signature.value,row.postCount.value,row.memberGroup.value,row.hotReplyIds.value))
        assertEquals(true,row.anonymous.value); assertEquals(true,row.muted.value)
        assertEquals(2.3f,row.reputation.value!!,0f)
        assertEquals(listOf("second","first"),row.attachments.value!!.keys.toList())
        val a = row.attachments.value!!["second"]!!.value!!
        assertEquals(listOf("A","原名.mp4","description","mp4","file","1","mon_test/a.mp4","video"),
            listOf(a.aid.value,a.urlUtf8OrgName.value,a.dscp.value,a.ext.value,a.name.value,a.thumb.value,a.attachUrl.value,a.type.value))
        assertEquals(12345,a.size.value);assertEquals(7,a.subid.value)
        assertEquals(ReadValueKind.MISSING,row.attachments.value!!["first"]!!.value!!.size.kind)
        // User absence leaves original row fallback data intact; WP/source/client mapping belongs to B4.
        assertNull(row.user.value)
    }

    @Test fun topicAliasesNavigationMetadataAndPageFieldsAreIndependentOfCurrentCount() {
        val obj = data()
        obj["__PAGE"] = 7; obj["__R__ROWS_PAGE"] = 30
        obj["__T"] = JSON.parseObject("""{"tid":120002,"fid":-7,"author":"author","authorid":42,
          "lastposter":"last","replies":899,"subject":"topic","titlefont":"b red","type":16,
          "topic_misc":"misc","postdate":1700000000,"page":7,"pid":51,"position":3,"anonymity":true,
          "board":"版面镜像","replyInfo":{"pidStr":"51","tidStr":"120002","authorId":"43","content":"reply","subject":"reply subject","postDate":"1700000001"}}""")
        val wire = success(obj); val topic = wire.topic.value!!
        assertEquals(900,wire.totalRowCount.value);assertEquals(1,wire.currentRowCount.value)
        assertEquals(1,wire.rows.value!!.size);assertEquals(7,wire.page.value);assertEquals(30,wire.rowsPerPage.value)
        assertEquals(listOf(120002,-7,42,899,16,1700000000,7,51,3),listOf(topic.tid.value,topic.fid.value,
            topic.authorId.value,topic.replies.value,topic.type.value,topic.postDate.value,topic.page.value,topic.pid.value,topic.position.value))
        assertEquals(listOf("author","last","topic","b red","misc","版面镜像"),listOf(topic.author.value,
            topic.lastPoster.value,topic.subject.value,topic.titleFont.value,topic.topicMisc.value,topic.board.value))
        assertEquals(true,topic.anonymity.value)
        val reply=topic.replyInfo.value!!
        assertEquals(listOf("51","120002","43","reply","reply subject","1700000001"),listOf(reply.pidStr.value,
            reply.tidStr.value,reply.authorId.value,reply.content.value,reply.subject.value,reply.postDate.value))
        assertEquals(42,wire.topicAuthorId.value)
    }

    @Test fun usersAreDecodedOncePerInvocationSharedWithCommentsAndNeverReplaceRowFallbacks() {
        val obj = data("""{"0":{"authorid":42,"author":"fallback","content":"first","comment":{"0":{"authorid":43,"content":"comment"},"1":{"authorid":42,"content":"same user"}}},"1":{"authorid":42,"content":"second"}}""",2)
        obj["__U"]=JSON.parseObject("""{"42":{"username":"#anony_01234567890123456789012345678901","avatar":"avatar",
          "yz":"-2","mute_time":"mute","rvrc":"12.5","signature":"sig","postnum":"999","memberid":7,
          "buffs":{"a":{},"b":0}},"43":{"username":"commenter","avatar":"comment-avatar"},
          "__GROUPS":{"7":{"0":"group"}}}""")
        val wire=success(obj); val first=wire.rows.value!![0];val second=wire.rows.value!![1]
        assertSame(first.user.value,second.user.value)
        assertSame(first.user.value,first.comments.value!![1].user.value)
        assertNotSame(first.user.value,first.comments.value!![0].user.value)
        assertEquals("comment-avatar",first.comments.value!![0].user.value!!.avatar.value)
        assertEquals("fallback",first.author.value)
        val user=first.user.value!!
        assertEquals(listOf("#anony_01234567890123456789012345678901","avatar","-2","mute","12.5","sig","999","7","group"),
            listOf(user.username.value,user.avatar.value,user.yz.value,user.muteTime.value,user.rvrc.value,user.signature.value,user.postCount.value,user.memberId.value,user.groupName.value))
        assertEquals(listOf("a","b"),user.buffIds.value)
        assertNotSame(user,success(obj).rows.value!![0].user.value)
        obj.getJSONObject("__U").getJSONObject("42")["username"]="changed later"
        assertEquals("#anony_01234567890123456789012345678901",user.username.value)
    }

    @Test fun missingNullAndWrongShapeRemainDistinctForOptionalFields() {
        val obj=data()
        var wire=success(obj)
        assertEquals(ReadValueKind.MISSING,wire.topic.kind)
        assertEquals(ReadValueKind.MISSING,wire.users.kind)
        assertEquals(ReadValueKind.MISSING,wire.global.kind)
        obj["__T"]=null;obj["__U"]=null;obj["__GLOBAL"]=null
        wire=success(obj)
        assertEquals(ReadValueKind.NULL,wire.topic.kind);assertEquals(ReadValueKind.NULL,wire.users.kind)
        assertEquals(ReadValueKind.NULL,wire.global.kind)
        obj["__GLOBAL"]="bad"
        wire=success(obj)
        assertEquals(ReadValueKind.STRING,wire.global.kind);assertFalse(wire.global.valid)
        obj["__GLOBAL"]=JSON.parseObject("""{"_ATTACH_BASE_VIEW":123}""")
        wire=success(obj);assertEquals(ReadValueKind.NUMBER,wire.attachmentBaseView.kind);assertFalse(wire.attachmentBaseView.valid)
        obj.getJSONObject("__GLOBAL")["_ATTACH_BASE_VIEW"]="https://page.invalid/full/attachments/"
        assertEquals("https://page.invalid/full/attachments/",success(obj).attachmentBaseView.value)
    }

    @Test fun sourceShapeRetainsExactTextAndValidityInsteadOfInventingAnEditableBody() {
        val values=listOf(null,"",123,java.math.BigDecimal("0.1234567890123456789"),true,JSON.parseObject("{\"x\":1}"),JSON.parseArray("[1,2]"))
        val kinds=listOf(ReadValueKind.NULL,ReadValueKind.STRING,ReadValueKind.NUMBER,ReadValueKind.NUMBER,
            ReadValueKind.BOOLEAN,ReadValueKind.OBJECT,ReadValueKind.ARRAY)
        for((index,value) in values.withIndex()) {
            val obj=data();val input=obj.getJSONObject("__R").getJSONObject("0")
            for(key in listOf("content","subject","alterinfo")) input[key]=value
            val row=success(obj).row()
            for(field in listOf(row.content,row.subject,row.alterInfo)) {
                assertEquals(kinds[index],field.kind);assertEquals(index<4,field.isSourceScalar)
                assertEquals(value?.toString(),field.value)
            }
            assertEquals(row,success(obj,ReadDecodeMode.LEGACY).row())
        }
        val obj=data();obj.getJSONObject("__R").getJSONObject("0").remove("content")
        assertEquals(ReadValueKind.MISSING,success(obj).row().content.kind)
        assertTrue(success(obj).row().content.isSourceScalar)
    }

    @Test fun strictCountAndIndexedRowFailuresHaveSeparateDispositionsAndValidationOrder() {
        for(value in listOf(null,-1,2)) {
            val obj=data();obj["__R__ROWS"]=value
            failure(obj,ReadShapeProblem.ROW_MAP_OR_COUNT)
        }
        for (value in listOf("bad", JSON.parseArray("[]"))) {
            val page = data(); page["__R__ROWS"] = value
            failure(page, ReadShapeProblem.UNREADABLE_VALUE)
            page.remove("__R") // Getter failure also precedes the row-map shape condition.
            failure(page, ReadShapeProblem.UNREADABLE_VALUE)
        }
        val obj=data();obj.remove("__R__ROWS");failure(obj,ReadShapeProblem.ROW_MAP_OR_COUNT)
        for(value in listOf(null,"bad",JSON.parseArray("[]"))) {
            val page=data();page["__R"]=value;failure(page,ReadShapeProblem.ROW_MAP_OR_COUNT)
        }
        for(row in listOf("null","123","[]","\"row\"")) {
            val page=data("""{"0":$row}""");page["__ROWS"]="also bad"
            failure(page,ReadShapeProblem.INDEXED_ROW)
        }
        failure(data("""{"2":{}}"""),ReadShapeProblem.INDEXED_ROW)
        // Coercible count passes scoped validation, but its original Int cast fails outside it.
        val coerced=data();coerced["__R__ROWS"]="1"
        failure(coerced,ReadShapeProblem.UNREADABLE_VALUE)
    }

    @Test fun legacySkipsBadIndexedRowsAndDoesNotScanTotalOrUnindexedKeys() {
        val obj=data("""{"0":null,"1":{"pid":52,"content":"kept"},"2":7,"outside":{"pid":999}}""",3)
        assertEquals(52,success(obj,ReadDecodeMode.LEGACY).row().pid.value)
        failure(obj,ReadShapeProblem.INDEXED_ROW)
        val missing=data();missing.remove("__R")
        val wire=success(missing,ReadDecodeMode.LEGACY)
        assertEquals(ReadValueKind.MISSING,wire.rows.kind);assertNull(wire.rows.value)
        val negative=data();negative["__R__ROWS"]=-1
        assertTrue(success(negative,ReadDecodeMode.LEGACY).rows.value!!.isEmpty())
        val zero=data("{}",0);assertTrue(success(zero).rows.value!!.isEmpty())
    }

    @Test fun nestedCommentGapsUseContentInScopedAndSkipInLegacy() {
        val obj=data("""{"0":{"content":"parent","comment":{"0":null,"1":{"pid":8,"content":"child"}}}}""")
        failure(obj,ReadShapeProblem.INDEXED_ROW)
        assertEquals(8,success(obj,ReadDecodeMode.LEGACY).row().comments.value!!.single().pid.value)
        val structured=data("""{"0":{"content":"parent","comment":{"0":{"content":{}}}}}""")
        assertFalse(success(structured).row().comments.value!!.single().content.isSourceScalar)
    }

    @Test fun malformedOptionalTopicDegradesWithoutDiscardingRowsOrOwnerField() {
        val obj=data();obj["__T"]=JSON.parseObject("""{"tid":"bad","authorid":42}""")
        val wire=success(obj)
        assertNull(wire.topic.value);assertFalse(wire.topic.valid)
        assertEquals(42,wire.topicAuthorId.value);assertEquals("body",wire.row().content.value)
        obj["__T"]=JSON.parseObject("{}")
        assertEquals(ReadValueKind.MISSING,success(obj).topic.value!!.tid.kind)
    }

    @Test fun absentUsersAndGroupsPreserveFallbackButReferencedBadUserValuesKeepOuterFailure() {
        val obj=data();obj["__U"]=JSON.parseObject("""{"unused":"bad","42":{"username":null,"rvrc":"bad","memberid":"missing"}}""")
        var wire=success(obj)
        assertEquals(ReadValueKind.NULL,wire.row().user.value!!.username.kind)
        assertNull(wire.row().user.value!!.groupName.value)
        assertFalse(wire.users.value!!["unused"]!!.valid)
        obj.getJSONObject("__U")["42"]=null
        assertNull(success(obj).row().user.value)
        obj.getJSONObject("__U")["42"]=JSON.parseArray("[]")
        failure(obj,ReadShapeProblem.UNREADABLE_VALUE)
        obj["__U"]=JSON.parseObject("""{"42":{"buffs":"{"}}""")
        failure(obj,ReadShapeProblem.UNREADABLE_VALUE)
        obj["__U"]=JSON.parseObject("""{"42":{"memberid":1},"__GROUPS":{"1":[]}}""")
        wire=success(obj);assertTrue(wire.row().user.value!!.groupName.valid)
        assertNull(wire.row().user.value!!.groupName.value)
    }

    @Test fun invalidPrimitiveAndContainerConversionsKeepOuterNullDispositionInBothModes() {
        val rows=listOf("""{"pid":"bad"}""","""{"comment":[]}""","""{"attachs":[]}""",
            """{"attachs":{"0":{"size":"bad"}}}""","""{"isanonymous":{}}""")
        for(row in rows) for(mode in ReadDecodeMode.values()) failure(data("""{"0":$row}"""),ReadShapeProblem.UNREADABLE_VALUE,mode)
        for(mode in ReadDecodeMode.values()) {
            val obj=data();obj["__ROWS"]="900";failure(obj,ReadShapeProblem.UNREADABLE_VALUE,mode)
            obj["__ROWS"]=900;obj["__U"]=JSON.parseArray("[]");failure(obj,ReadShapeProblem.UNREADABLE_VALUE,mode)
        }
    }


    @Test fun wrongTopicContainerKeepsOuterFailureInsteadOfOptionalBeanDegradation() {
        for (value in listOf(JSON.parseArray("[]"), "{}", 7, true)) {
            for (mode in ReadDecodeMode.values()) {
                val obj = data(); obj["__T"] = value
                failure(obj, ReadShapeProblem.UNREADABLE_VALUE, mode)
            }
        }
    }

    @Test fun groupAndBuffGettersRetainStringObjectCoercionAndNonobjectNullResults() {
        val obj = data()
        obj["__U"] = JSON.parseObject("""{"42":{"memberid":"7"}}""")
        val users = obj.getJSONObject("__U")
        val user = users.getJSONObject("42")
        val groups = JSONObject()
        groups["7"] = """{"0":"string group"}"""
        users["__GROUPS"] = groups.toJSONString()
        user["buffs"] = """{"first":{},"second":0}"""
        val wire = success(obj)
        assertEquals(ReadValueKind.STRING, wire.groups.kind)
        assertEquals("string group", wire.row().user.value!!.groupName.value)
        assertEquals(ReadValueKind.STRING, wire.row().user.value!!.groupName.kind)
        assertEquals(ReadValueKind.STRING, wire.row().user.value!!.buffIds.kind)
        assertEquals(listOf("first", "second"), wire.row().user.value!!.buffIds.value)
        for (value in listOf(JSON.parseArray("[]"), 7, true, "", "null")) {
            users["__GROUPS"] = value; user["buffs"] = value
            val converted = success(obj).row().user.value!!
            assertTrue(converted.buffIds.valid)
            assertNull(converted.buffIds.value)
            assertNull(converted.groupName.value)
        }
        users["__GROUPS"] = "{"
        failure(obj, ReadShapeProblem.UNREADABLE_VALUE)
        users.remove("42")
        failure(obj, ReadShapeProblem.UNREADABLE_VALUE) // Groups are read before missing-user return.
        // The original parser never reads groups for an absent author association.
        obj.getJSONObject("__R").getJSONObject("0")["authorid"] = 0
        assertEquals("body", success(obj).row().content.value)
    }

    @Test fun primitiveFailurePrecedesNestedGapAndBadOwnerOnlyMattersForConvertedRows() {
        failure(data("""{"0":{"score":"bad","comment":{"1":{}}}}"""),ReadShapeProblem.UNREADABLE_VALUE)
        val obj=data();obj["__T"]=JSON.parseObject("""{"authorid":"bad"}""")
        failure(obj,ReadShapeProblem.UNREADABLE_VALUE)
        obj["__R__ROWS"]=0;obj["__R"]=JSON.parseObject("{}")
        assertTrue(success(obj).rows.value!!.isEmpty())
    }
}
