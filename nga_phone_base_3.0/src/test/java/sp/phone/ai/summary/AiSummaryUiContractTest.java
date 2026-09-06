package sp.phone.ai.summary;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

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
    public void sharedDialogIsScrollableAndDismissalCancelsItsController() throws IOException {
        assertTrue(read("res/layout/dialog_ai_summary.xml").contains("<ScrollView"));
        String dialog = read("java/sp/phone/ui/fragment/dialog/AiSummaryDialog.java");
        assertTrue(method(dialog, "void dismiss()").contains("controller.cancel()"));
        assertTrue(dialog.contains("setOnDismissListener"));
        assertTrue(dialog.contains("SettingsAiFragment.open(context)"));
        assertFalse(dialog.contains("new Bundle"));
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
