package sp.phone.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AiProfilePromptTest {
    @Test
    public void defaultIsRoastAndTheTwoPresetsHaveDistinctInstructions() {
        AiProfilePrompt roast = AiProfilePrompt.DEFAULT;
        AiProfilePrompt detailed = new AiProfilePrompt(AiProfilePrompt.Style.DETAILED, "");
        assertEquals(AiProfilePrompt.Style.FORUM_ROAST, roast.getStyle());
        assertNotEquals(roast.getInstructions(), detailed.getInstructions());
        assertTrue(roast.getInstructions().contains("回复正文在1000字以内"));
        assertTrue(detailed.getInstructions().contains("回复正文在1000字以内"));
    }

    @Test
    public void customInstructionsKeepWhitespaceNewlinesAndUnicodeExactly() {
        String text = "  第一段 😀\r\n\t第二段\n\n";
        AiProfilePrompt custom = new AiProfilePrompt(AiProfilePrompt.Style.CUSTOM, text);
        assertEquals(text, custom.getCustomText());
        assertEquals(text, custom.getInstructions());
        assertEquals(custom, new AiProfilePrompt(AiProfilePrompt.Style.CUSTOM, text));
    }

    @Test
    public void presetRetainsCustomTextWithoutUsingItAsInstructions() {
        String text = "CUSTOM_SENTINEL\nSecond line";
        for (AiProfilePrompt.Style style : new AiProfilePrompt.Style[]{
                AiProfilePrompt.Style.FORUM_ROAST, AiProfilePrompt.Style.DETAILED}) {
            AiProfilePrompt preset = new AiProfilePrompt(style, text);
            assertEquals(text, preset.getCustomText());
            assertEquals(new AiProfilePrompt(style, "").getInstructions(), preset.getInstructions());
            assertFalse(preset.getInstructions().contains("CUSTOM_SENTINEL"));
            assertNotEquals(preset, new AiProfilePrompt(style, ""));
        }
    }

    @Test
    public void blankCustomAndOversizedRetainedTextAreRejectedWithoutTruncation() {
        for (String blank : new String[]{"", " \r\n\t", "\u00a0\u3000\u2003"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new AiProfilePrompt(AiProfilePrompt.Style.CUSTOM, blank));
            assertEquals(blank, new AiProfilePrompt(AiProfilePrompt.Style.DETAILED, blank).getCustomText());
        }
        String limit = "中".repeat(AiProfilePrompt.MAX_CUSTOM_PROMPT_CHARS);
        assertEquals(limit, new AiProfilePrompt(AiProfilePrompt.Style.CUSTOM, limit).getInstructions());
        for (AiProfilePrompt.Style style : AiProfilePrompt.Style.values()) {
            assertThrows(IllegalArgumentException.class, () -> new AiProfilePrompt(style, limit + "x"));
            assertThrows(IllegalArgumentException.class, () -> new AiProfilePrompt(style, null));
        }
        assertThrows(IllegalArgumentException.class, () -> new AiProfilePrompt(null, ""));
    }

    @Test
    public void diagnosticsNeverIncludeCustomText() {
        String text = "CUSTOM_PRIVATE_SENTINEL";
        AiProfilePrompt prompt = new AiProfilePrompt(AiProfilePrompt.Style.CUSTOM, text);
        assertFalse(prompt.toString().contains(text));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new AiProfilePrompt(AiProfilePrompt.Style.CUSTOM, text.repeat(8192)));
        assertFalse(error.toString().contains(text));
        assertNull(error.getCause());
    }
}
