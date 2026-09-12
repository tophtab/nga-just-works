package sp.phone.mvp.model.thread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

/** Decoded synthetic bodies only; classification must stop before any alternate request. */
class ArticleErrorsTest {
    private val query = ArticleQuery(100001, 0, 0, 0)
    private val noRender = ArticleRowRenderer { _, _ -> fail("A terminal body must not render") }
    private val noBlacklist = ArticleBlacklist { false }

    private fun assertTerminal(raw: String, kind: ArticleFailureKind) {
        val parsers: List<() -> Unit> = listOf(
            { ArticleErrors.json(raw) },
            { NormalArticleParser(noRender, noBlacklist).parse(raw, query, 1) },
            { AppArticleParser(noRender, noBlacklist).parse(raw, query, 1) },
        )
        for (parse in parsers) {
            try {
                parse()
                fail("Expected a terminal response")
            } catch (failure: ArticleFailure) {
                assertEquals(kind, failure.kind)
                assertFalse(failure.allowsAppFallback())
                assertFalse(ArticleAttemptPolicy().tryApp(failure, true, true, true))
            }
        }
    }

    @Test fun bomPrefixedUnknownHtmlIsStillAnAccessStop() {
        for (prefix in listOf("", "\uFEFF", " \r\n\uFEFF\t")) {
            assertTerminal(prefix + "<!doctype html><html>synthetic page</html>", ArticleFailureKind.ACCESS)
        }
    }

    @Test fun bomAndWhitespaceWithoutContentRemainEmpty() {
        for (raw in listOf("", " \n\t", "\uFEFF", " \r\n\uFEFF\t\u3000")) {
            assertTerminal(raw, ArticleFailureKind.EMPTY)
        }
    }
}
