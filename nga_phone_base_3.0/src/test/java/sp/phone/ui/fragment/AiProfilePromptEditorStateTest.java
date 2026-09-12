package sp.phone.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import sp.phone.ai.AiProfilePrompt;

public class AiProfilePromptEditorStateTest {
    @Test
    public void freshSettingsAndAnOpenedEditorBothDefaultToRoast() {
        AiProfilePromptEditorState state = new AiProfilePromptEditorState();
        assertEquals(AiProfilePrompt.DEFAULT, state.getPrompt());
        long generation = state.open();
        assertTrue(state.isActive(generation));
        assertEquals(AiProfilePrompt.Style.FORUM_ROAST, state.getSelectedStyle());
        assertEquals("", state.getCustomText());
    }

    @Test
    public void switchingPresetsAndReopeningRetainsExactCustomText() {
        AiProfilePromptEditorState state = new AiProfilePromptEditorState();
        String text = "  First line\r\n\t第二行 😀\n";
        state.open();
        state.selectStyle(AiProfilePrompt.Style.CUSTOM);
        state.setCustomText(text);
        state.selectStyle(AiProfilePrompt.Style.DETAILED);
        state.selectStyle(AiProfilePrompt.Style.FORUM_ROAST);
        assertEquals(text, state.getCustomText());
        assertEquals(AiProfilePrompt.DEFAULT, state.getPrompt());
        AiProfilePrompt accepted = state.confirm();
        assertEquals(AiProfilePrompt.Style.FORUM_ROAST, accepted.getStyle());
        assertEquals(text, accepted.getCustomText());
        assertFalse(state.isEditing());

        state.open();
        state.selectStyle(AiProfilePrompt.Style.CUSTOM);
        assertEquals(text, state.getCustomText());
        assertEquals(text, state.confirm().getInstructions());
    }

    @Test
    public void cancelDiscardsEditsButPreservesThePreviouslyAcceptedSettingsDraft() {
        AiProfilePromptEditorState state = new AiProfilePromptEditorState();
        state.reset(new AiProfilePrompt(AiProfilePrompt.Style.CUSTOM, "Saved custom\n"));
        state.open();
        state.selectStyle(AiProfilePrompt.Style.DETAILED);
        AiProfilePrompt acceptedDraft = state.confirm();

        long cancelled = state.open();
        state.selectStyle(AiProfilePrompt.Style.CUSTOM);
        state.setCustomText("Cancelled replacement");
        state.close();
        assertSame(acceptedDraft, state.getPrompt());
        assertEquals("", state.getCustomText());
        assertFalse(state.isActive(cancelled));
        state.setCustomText("Late dismissed-view event");
        long reopened = state.open();
        assertTrue(state.isActive(reopened));
        assertFalse(state.isActive(cancelled));
        assertEquals(AiProfilePrompt.Style.DETAILED, state.getSelectedStyle());
        assertEquals("Saved custom\n", state.getCustomText());
    }

    @Test
    public void invalidCustomCannotReplaceTheDraftAndCanBeCorrectedInTheSameEditor() {
        AiProfilePromptEditorState state = new AiProfilePromptEditorState();
        long generation = state.open();
        state.selectStyle(AiProfilePrompt.Style.CUSTOM);
        for (String invalid : new String[]{"", "\n\u3000\u00a0", "x".repeat(8193)}) {
            state.setCustomText(invalid);
            assertThrows(IllegalArgumentException.class, state::confirm);
            assertTrue(state.isActive(generation));
            assertEquals(invalid, state.getCustomText());
            assertEquals(AiProfilePrompt.DEFAULT, state.getPrompt());
        }
        state.selectStyle(AiProfilePrompt.Style.DETAILED);
        assertThrows(IllegalArgumentException.class, state::confirm);
        state.selectStyle(AiProfilePrompt.Style.CUSTOM);
        state.setCustomText("Corrected prompt");
        assertEquals("Corrected prompt", state.confirm().getInstructions());
        assertFalse(state.isActive(generation));
    }

    @Test
    public void reloadingSavedSettingsReplacesBothTheDraftAndAnyOpenEditor() {
        AiProfilePromptEditorState state = new AiProfilePromptEditorState();
        long generation = state.open();
        state.selectStyle(AiProfilePrompt.Style.CUSTOM);
        state.setCustomText("Unsaved editor text");
        AiProfilePrompt saved = new AiProfilePrompt(AiProfilePrompt.Style.DETAILED, "Retained custom");
        state.reset(saved);
        assertSame(saved, state.getPrompt());
        assertFalse(state.isActive(generation));
        assertThrows(IllegalStateException.class, state::confirm);
        state.open();
        assertEquals(AiProfilePrompt.Style.DETAILED, state.getSelectedStyle());
        assertEquals("Retained custom", state.getCustomText());
    }
}
