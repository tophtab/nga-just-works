package sp.phone.view.editor;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.*;

/** JVM checks for the thin Android integration; behavioral parsing/queue tests are separate. */
public class InlineMediaEditorWiringTest {
    private String source(String relative) throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
        assertNotNull(root);
        return new String(Files.readAllBytes(root.resolve("nga_phone_base_3.0/src/main/" + relative)), StandardCharsets.UTF_8);
    }

    @Test public void restorationHasOneSourceOwnerAndNoOriginalBodyAppend() throws Exception {
        String fragment = source("java/sp/phone/ui/fragment/TopicPostFragment.java");
        String presenter = source("java/sp/phone/mvp/presenter/TopicPostPresenter.java");
        assertFalse(presenter.contains("void onViewCreated()"));
        assertFalse(presenter.contains("insertBodyText(mPostParam.getPostContent())"));
        assertTrue(fragment.contains("draft.containsKey(\"body\")")); // Empty restored draft wins too.
        assertTrue(fragment.contains("mRetainedDraft != null ? mRetainedDraft : savedInstanceState"));
        assertTrue(fragment.contains("state.putString(\"body\", mBodyEditText.getText().toString())"));
        assertTrue(fragment.contains("state.putInt(\"selectionStart\""));
        assertTrue(fragment.contains("state.putBoolean(\"anony\""));
        assertTrue(fragment.contains("mMediaDecorator.close()"));
        assertTrue(fragment.contains("mMediaDecorator.suspend()"));
        assertTrue(source("res/layout/fragment_topic_post.xml").contains("sp.phone.view.editor.InlineMediaEditText"));
        assertTrue(source("res/layout/fragment_topic_post.xml").contains("android:saveEnabled=\"false\""));
    }

    @Test public void mediaLoadsHaveNoPostingPathAndCheckSettingsBeforeAndAfterLoad() throws Exception {
        String decorator = source("java/sp/phone/view/editor/InlineMediaDecorator.java");
        assertFalse(decorator.contains("uploadFile"));
        assertFalse(decorator.contains("Cookie"));
        assertFalse(decorator.contains(".setText("));
        assertFalse(decorator.contains(".setSelection("));
        assertTrue(decorator.contains("PhoneConfiguration.getInstance().isImageLoadEnabled()"));
        assertTrue(decorator.contains("revision.accepts(generation, token, editor.getText())"));
        assertTrue(decorator.contains("requests.clear(target)"));
        assertTrue(decorator.contains(".override(size, size)"));
        assertTrue(decorator.contains("DiskCacheStrategy.AUTOMATIC"));
        String editor = source("java/sp/phone/view/editor/InlineMediaEditText.java");
        assertTrue(editor.contains("extends SpannableStringBuilder"));
        assertTrue(editor.contains("super.replace(range[0], range[1], source, sourceStart, sourceEnd)"));
        assertTrue(editor.contains("InlineMediaSource.expand(start, end, displayedRanges(getText()))"));
    }
}
