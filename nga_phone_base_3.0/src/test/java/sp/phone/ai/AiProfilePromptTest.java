package sp.phone.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class AiProfilePromptTest {
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
