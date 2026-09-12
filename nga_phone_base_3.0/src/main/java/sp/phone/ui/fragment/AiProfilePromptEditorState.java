package sp.phone.ui.fragment;

import sp.phone.ai.AiProfilePrompt;

/** Separates the accepted settings draft from edits in an open, cancellable prompt dialog. */
final class AiProfilePromptEditorState {
    private AiProfilePrompt prompt = AiProfilePrompt.DEFAULT;
    private AiProfilePrompt.Style selectedStyle;
    private String customText = "";
    private boolean editing;
    private long generation;

    void reset(AiProfilePrompt value) {
        close();
        prompt = value;
    }

    AiProfilePrompt getPrompt() {
        return prompt;
    }

    long open() {
        close();
        selectedStyle = prompt.getStyle();
        customText = prompt.getCustomText();
        editing = true;
        return generation;
    }

    boolean isEditing() {
        return editing;
    }

    boolean isActive(long editorGeneration) {
        return editing && generation == editorGeneration;
    }

    AiProfilePrompt.Style getSelectedStyle() {
        return selectedStyle;
    }

    void selectStyle(AiProfilePrompt.Style style) {
        if (editing) {
            selectedStyle = style;
        }
    }

    String getCustomText() {
        return customText;
    }

    void setCustomText(String text) {
        if (editing) {
            customText = text;
        }
    }

    AiProfilePrompt confirm() {
        if (!editing) {
            throw new IllegalStateException("Profile prompt editor is closed");
        }
        // Validation must succeed before the accepted draft is replaced or the dialog is closed.
        AiProfilePrompt updated = new AiProfilePrompt(selectedStyle, customText);
        prompt = updated;
        close();
        return prompt;
    }

    void close() {
        generation++;
        editing = false;
        selectedStyle = null;
        customText = "";
    }
}
