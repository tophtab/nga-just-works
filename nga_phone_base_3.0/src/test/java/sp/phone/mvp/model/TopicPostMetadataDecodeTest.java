package sp.phone.mvp.model;
import org.junit.Test;
import static org.junit.Assert.*;
public class TopicPostMetadataDecodeTest {
    @Test public void preflightUsesAuthAndLeavesErrorHandlingInCaller() {
        assertEquals("fixture-auth", TopicPostModel.decodePreflight("{\"data\":{\"auth\":\"fixture-auth\",\"fid\":42}}"));
        assertNull(TopicPostModel.decodePreflight("{\"data\":{}}"));
        assertThrows(RuntimeException.class, () -> TopicPostModel.decodePreflight("{\"error\":{\"0\":\"denied\"}}"));
        assertThrows(RuntimeException.class, () -> TopicPostModel.decodePreflight("{"));
    }
    @Test public void categoriesStopAtFirstMissingIndexAndRetainNullLabels() {
        assertEquals(java.util.Arrays.asList("first", null), TopicPostModel.decodeTopicCategories("{\"data\":{\"0\":{\"0\":{\"0\":\"first\"},\"1\":{},\"3\":{\"0\":\"skipped\"}}}}"));
        assertTrue(TopicPostModel.decodeTopicCategories("{\"data\":{\"0\":{}}}").isEmpty());
        assertThrows(RuntimeException.class, () -> TopicPostModel.decodeTopicCategories("{\"error\":{\"0\":\"denied\"}}"));
        assertThrows(RuntimeException.class, () -> TopicPostModel.decodeTopicCategories("{"));
    }
}
