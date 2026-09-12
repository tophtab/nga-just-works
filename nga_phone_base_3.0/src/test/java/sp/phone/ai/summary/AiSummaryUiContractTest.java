package sp.phone.ai.summary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Characterizes the legacy page-to-controller wiring; controller behavior is executed separately. */
public class AiSummaryUiContractTest {

    @Test
    public void bothFloorMenusExposeTheSameActionAndProfileStartsHidden() throws IOException {
        for (String menu : new String[]{"article_list_context_menu.xml", "article_list_context_menu_with_tid.xml"}) {
            assertTrue(read("res/menu/" + menu).contains("@+id/menu_ai_summary"));
        }
        String profileMenu = read("res/menu/menu_user_profile.xml");
        String summaryItem = profileMenu.substring(profileMenu.indexOf("@+id/menu_ai_summary"));
        summaryItem = summaryItem.substring(0, summaryItem.indexOf("/>"));
        assertTrue(summaryItem.contains("android:visible=\"false\""));
    }

    @Test
    public void retainedArticlePagesCancelOnPauseAndDataReplacement() throws IOException {
        String article = read("java/sp/phone/ui/fragment/ArticleListFragment.java");
        assertTrue(method(article, "void onPause()").contains("dismissAiSummary()"));
        assertTrue(method(article, "void onDestroyView()").contains("dismissAiSummary()"));
        assertTrue(method(article, "void setData(ThreadData data)").contains("dismissAiSummary()"));
        assertTrue(method(article, "void showAiSummary(ThreadRowInfo row)")
                .contains("FloorSummaryInput.fromRow(title, row)"));
        assertTrue(read("java/sp/phone/ui/adapter/ArticlePagerAdapter.java")
                .contains("BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT"));
    }

    @Test
    public void profileUsesLoadedUidAndCancelsWhenThePageIsPaused() throws IOException {
        String profile = read("java/gov/anzong/androidnga/activity/ProfileActivity.java");
        String action = method(profile, "void showAiSummary()");
        assertTrue(action.contains("mProfileData.getUid()"));
        assertTrue(action.contains("AiSummaryDialog.showProfile(this, uid, userName"));
        assertFalse(action.contains("UserManager"));
        assertTrue(method(profile, "void onPause()").contains("dismissAiSummary()"));
        assertTrue(method(profile, "void onSuccess(ProfileData data)").contains("dismissAiSummary()"));
    }

    @Test
    public void profileSourceUsesTheControllerConfigurationAndDefersSessionReads() throws IOException {
        String source = read("java/sp/phone/ai/summary/AiSummarySources.java");
        String profile = method(source, "InputSource profile(");
        int deferredLoad = profile.indexOf("return (config, callback) ->");
        assertTrue(deferredLoad >= 0);
        assertTrue(profile.indexOf("new NgaProfilePageSource(") > deferredLoad);
        assertTrue(profile.indexOf("UserManagerImpl.getInstance().getCookie()") > deferredLoad);
        assertTrue(profile.contains(".load(uid, userName, config.getProfilePrompt(), callback)"));
        assertFalse(source.contains("AiConfigStore"));
        String floor = method(source, "InputSource floor(");
        assertTrue(floor.contains("callback.onSuccess(input.toPrompt())"));
        assertFalse(floor.contains("getProfilePrompt()"));
    }

    @Test
    public void sharedDialogIsScrollableAndDismissalCancelsItsController() throws IOException {
        assertTrue(read("res/layout/dialog_ai_summary.xml").contains("<ScrollView"));
        String dialog = read("java/sp/phone/ui/fragment/dialog/AiSummaryDialog.java");
        assertTrue(method(dialog, "void dismiss()").contains("controller.cancel()"));
        String dismissal = method(dialog, "setOnDismissListener");
        assertTrue(dismissal.contains("controller.cancel()"));
        assertTrue(dismissal.contains("setReasoningExpanded(false)"));
        assertTrue(dismissal.contains("render(controller.getState())"));
        assertTrue(dismissal.indexOf("controller.cancel()") < dismissal.indexOf("render("));
        assertTrue(dialog.contains("SettingsAiFragment.open(context)"));
        assertFalse(dialog.contains("new Bundle"));
    }

    @Test
    public void sharedDialogForwardsBothStreamingChannelsAndRendersErrorsSeparately() throws IOException {
        String dialog = read("java/sp/phone/ui/fragment/dialog/AiSummaryDialog.java");
        assertTrue(method(dialog, "void onProgress(String answer, String reasoning)")
                .contains("callback.onProgress(answer, reasoning)"));
        String render = method(dialog, "void render(SummaryController.State state)");
        assertTrue(render.contains("content.setText(state.getAnswer())"));
        assertTrue(render.contains("error.setText(state.getErrorMessage())"));
        assertTrue(render.contains("status.setVisibility(loading ? View.VISIBLE : View.GONE)"));
        assertTrue(render.contains("renderReasoning(state)"));
        assertFalse(render.contains("state.getText()"));
        assertFalse(render.contains("content.setText(R.string.ai_summary_loading)"));
        String reasoning = method(dialog, "void renderReasoning(SummaryController.State state)");
        assertTrue(reasoning.contains("reasoning.setText(state.getReasoning())"));
        assertFalse(reasoning.contains("content.setText"));
    }

    @Test
    public void reasoningFoldChangesOnlyOnUserToggleOrRetryAndSurvivesProgressRendering() throws IOException {
        String dialog = read("java/sp/phone/ui/fragment/dialog/AiSummaryDialog.java");
        assertTrue(dialog.contains("reasoningToggle.setOnClickListener(button -> "
                + "setReasoningExpanded(!reasoningExpanded))"));
        String retry = method(dialog, "void retry()");
        assertTrue(retry.contains("setReasoningExpanded(false)"));
        assertTrue(retry.indexOf("setReasoningExpanded(false)") < retry.indexOf("controller.start("));
        String render = method(dialog, "void render(SummaryController.State state)");
        String reasoning = method(dialog, "void renderReasoning(SummaryController.State state)");
        assertFalse(render.contains("setReasoningExpanded"));
        assertFalse(reasoning.contains("setReasoningExpanded"));
        assertFalse(reasoning.contains("reasoningExpanded ="));
        assertTrue(reasoning.contains("hasReasoning && reasoningExpanded ? View.VISIBLE : View.GONE"));
        String toggle = method(dialog, "void setReasoningExpanded(boolean expanded)");
        assertTrue(toggle.contains("reasoningExpanded = expanded"));
        assertTrue(toggle.contains("R.string.ai_summary_hide_reasoning"));
        assertTrue(toggle.contains("R.string.ai_summary_show_reasoning"));
        assertTrue(toggle.contains("ViewCompat.setStateDescription(reasoningToggle"));
        assertTrue(toggle.contains("R.string.ai_summary_reasoning_expanded"));
        assertTrue(toggle.contains("R.string.ai_summary_reasoning_collapsed"));
        assertFalse(toggle.contains("content.setText"));
        assertFalse(toggle.contains("controller.start"));
    }

    @Test
    public void copyReadsTheCompleteAnswerProjectionIndependentOfReasoningVisibility() throws IOException {
        String dialog = read("java/sp/phone/ui/fragment/dialog/AiSummaryDialog.java");
        String copy = method(dialog, "void copy(Context context)");
        assertTrue(copy.contains("String copyText = controller.getState().getCopyText()"));
        assertTrue(copy.contains("if (copyText.isEmpty())"));
        assertTrue(copy.contains("context.getString(R.string.ai_summary_action), copyText)"));
        assertFalse(copy.contains("getText()"));
        assertFalse(copy.contains("getReasoning()"));
        assertFalse(copy.contains("reasoningExpanded"));
        assertFalse(copy.contains("substring("));
        String render = method(dialog, "void render(SummaryController.State state)");
        assertTrue(render.contains("boolean canCopy = !state.getCopyText().isEmpty()"));
        assertTrue(render.contains("BUTTON_NEUTRAL).setEnabled(canCopy)"));
        assertTrue(render.contains("content.setTextIsSelectable(canCopy)"));
    }

    @Test
    public void layoutKeepsReasoningFoldedAndScrollableWithoutClippingOrLiveTokenAnnouncements() throws Exception {
        Document layout = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new InputSource(new StringReader(read("res/layout/dialog_ai_summary.xml"))));
        Element answer = view(layout, "ai_summary_content");
        Element reasoning = view(layout, "ai_summary_reasoning");
        Element toggle = view(layout, "ai_summary_reasoning_toggle");
        Element status = view(layout, "ai_summary_status");
        Element error = view(layout, "ai_summary_error");
        assertEquals("Button", toggle.getTagName());
        assertEquals("48dp", toggle.getAttribute("android:minHeight"));
        assertEquals("@string/ai_summary_show_reasoning", toggle.getAttribute("android:text"));
        assertEquals("gone", toggle.getAttribute("android:visibility"));
        assertEquals("gone", reasoning.getAttribute("android:visibility"));
        assertEquals("@string/ai_summary_loading", status.getAttribute("android:text"));
        assertEquals("", answer.getAttribute("android:text"));
        assertEquals("gone", error.getAttribute("android:visibility"));
        assertEquals("ScrollView", answer.getParentNode().getParentNode().getNodeName());
        assertEquals(answer.getParentNode(), reasoning.getParentNode());
        assertEquals(answer.getParentNode(), toggle.getParentNode());
        assertEquals(answer.getParentNode(), error.getParentNode());
        for (Element streamedText : new Element[]{answer, reasoning}) {
            assertEquals("none", streamedText.getAttribute("android:accessibilityLiveRegion"));
            assertEquals("false", streamedText.getAttribute("android:saveEnabled"));
            assertFalse(streamedText.hasAttribute("android:maxLength"));
            assertFalse(streamedText.hasAttribute("android:maxLines"));
            assertFalse(streamedText.hasAttribute("android:ellipsize"));
        }
    }

    private static Element view(Document layout, String id) {
        NodeList elements = layout.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (("@+id/" + id).equals(element.getAttribute("android:id"))) {
                return element;
            }
        }
        throw new AssertionError("Missing view: " + id);
    }

    private static String method(String source, String signature) {
        int signatureStart = source.indexOf(signature);
        if (signatureStart < 0) {
            throw new AssertionError("Missing method: " + signature);
        }
        int start = source.indexOf('{', signatureStart);
        int nesting = 1;
        int end = start + 1;
        while (nesting > 0 && end < source.length()) {
            char value = source.charAt(end++);
            if (value == '{') nesting++;
            if (value == '}') nesting--;
        }
        return source.substring(start, end);
    }

    private static String read(String path) throws IOException {
        Path root = Paths.get("src/main");
        if (!Files.exists(root)) {
            root = Paths.get("nga_phone_base_3.0/src/main");
        }
        return new String(Files.readAllBytes(root.resolve(path)), StandardCharsets.UTF_8);
    }
}
