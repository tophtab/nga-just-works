package com.justwen.androidnga.module.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyMessageJsonTest {

    @Test
    fun stripsScriptWrapperAndJsMarker() {
        assertEquals(
            """{"data":{"0":"ok"}}""",
            LegacyMessageJson.stripWrapper("window.script_muti_get_var_store={\"data\":{\"0\":\"ok\"}}"),
        )
        assertEquals(
            """{"data":1}""",
            LegacyMessageJson.stripWrapper("""{"data":1}/*${'$'}js${'$'}*/"""),
        )
    }

    @Test
    fun truncatesAtErrorFillComment() {
        assertEquals(
            """{"data":1}""",
            LegacyMessageJson.truncateErrorFill("""{"data":1}/*error fill content<div>"""),
        )
        assertEquals(
            """{"data":1}""",
            LegacyMessageJson.truncateErrorFill("""{"data":1}"""),
        )
    }

    @Test
    fun quotesPlusAndLeadingZeroNumericTokens() {
        val raw = """{"content":+123,"subject":+7,"author":+1,"other":+5}"""
        assertEquals(
            """{"content":"+123","subject":"+7","author":+1,"other":+5}""",
            LegacyMessageJson.repairNumericTokens(raw),
        )
        assertEquals(
            """{"content":"0123","subject":"07","x":1}""",
            LegacyMessageJson.repairNumericTokens("""{"content":0123,"subject":07,"x":1}"""),
        )
    }

    @Test
    fun repairsAuthorOnlyWhenDetailRequestsIt() {
        val raw = """{"content":"c","author":0123,"x":1}"""
        assertTrue(LegacyMessageJson.repairNumericTokens(raw).contains("\"author\":0123"))
        assertEquals(
            """{"content":"c","author":"0123","x":1}""",
            LegacyMessageJson.repairNumericTokens(raw, includeAuthor = true),
        )
    }
}
