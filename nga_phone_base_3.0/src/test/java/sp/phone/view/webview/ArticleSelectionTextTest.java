package sp.phone.view.webview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArticleSelectionTextTest {

    @Test
    public void missingSelectionsDecodeToAnEmptyString() {
        assertEquals("", ArticleSelectionText.decodeEvaluatedString(null));
        assertEquals("", ArticleSelectionText.decodeEvaluatedString(""));
        assertEquals("", ArticleSelectionText.decodeEvaluatedString("null"));
        assertEquals("", ArticleSelectionText.decodeEvaluatedString("\"\""));
    }

    @Test
    public void quotedResultsAreUnwrappedAndUnescaped() {
        assertEquals("hello", ArticleSelectionText.decodeEvaluatedString("\"hello\""));
        assertEquals("say \"hi\"", ArticleSelectionText.decodeEvaluatedString("\"say \\\"hi\\\"\""));
        assertEquals("a\\b", ArticleSelectionText.decodeEvaluatedString("\"a\\\\b\""));
        assertEquals("a/b", ArticleSelectionText.decodeEvaluatedString("\"a\\/b\""));
        assertEquals("line1\nline2", ArticleSelectionText.decodeEvaluatedString("\"line1\\nline2\""));
        assertEquals("col1\tcol2", ArticleSelectionText.decodeEvaluatedString("\"col1\\tcol2\""));
        assertEquals("a\r\nb", ArticleSelectionText.decodeEvaluatedString("\"a\\r\\nb\""));
    }

    @Test
    public void unicodeEscapesAreRestored() {
        assertEquals("中文", ArticleSelectionText.decodeEvaluatedString("\"\\u4e2d\\u6587\""));
        assertEquals("中X", ArticleSelectionText.decodeEvaluatedString("\"\\u4e2dX\""));
    }

    @Test
    public void malformedEscapesDegradeWithoutThrowing() {
        assertEquals("u4e", ArticleSelectionText.decodeEvaluatedString("\"\\u4e\""));
        assertEquals("uzzzz", ArticleSelectionText.decodeEvaluatedString("\"\\uzzzz\""));
        assertEquals("a\\", ArticleSelectionText.decodeEvaluatedString("\"a\\\""));
    }

    @Test
    public void blankDetectionCoversNonAsciiWhitespace() {
        assertTrue(ArticleSelectionText.isBlank(null));
        assertTrue(ArticleSelectionText.isBlank(""));
        assertTrue(ArticleSelectionText.isBlank("   "));
        assertTrue(ArticleSelectionText.isBlank("\u00a0"));
        assertTrue(ArticleSelectionText.isBlank("\u3000"));
        assertTrue(ArticleSelectionText.isBlank("\u2003"));
        assertTrue(ArticleSelectionText.isBlank(" \t\n\u00a0\u3000 "));
    }

    @Test
    public void blankDetectionRejectsAnyVisibleCharacter() {
        assertFalse(ArticleSelectionText.isBlank("a"));
        assertFalse(ArticleSelectionText.isBlank("  中  "));
        assertFalse(ArticleSelectionText.isBlank("\u00a0.\u3000"));
    }
}
