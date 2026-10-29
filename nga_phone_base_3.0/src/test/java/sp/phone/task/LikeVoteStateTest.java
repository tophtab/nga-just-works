package sp.phone.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * NGA topic_recommend toggles: data["1"] is the score change of this request. Sequence observed by
 * MNGA against the live API: up +1, up again -1 (cancel), up +1, down -2 (switch), down again +1.
 */
public class LikeVoteStateTest {

    @Test
    public void decodesTheScoreDelta() {
        assertEquals(Integer.valueOf(1), LikeTask.decodeDelta("{\"data\":{\"0\":\"ok\",\"1\":1}}"));
        assertEquals(Integer.valueOf(-2), LikeTask.decodeDelta("{\"data\":{\"0\":\"ok\",\"1\":\"-2\"}}"));
        assertNull(LikeTask.decodeDelta("{\"data\":{\"0\":\"ok\"}}"));
        assertNull(LikeTask.decodeDelta("{\"data\":{\"0\":\"ok\",\"1\":\"x\"}}"));
        assertNull(LikeTask.decodeDelta("{"));
    }

    @Test
    public void stateFollowsTheDeltaSign() {
        assertEquals(LikeTask.STATE_UP, LikeTask.stateAfter(LikeTask.SUPPORT, 1));
        assertEquals(LikeTask.STATE_NONE, LikeTask.stateAfter(LikeTask.SUPPORT, -1));
        assertEquals(LikeTask.STATE_DOWN, LikeTask.stateAfter(LikeTask.OPPOSE, -2));
        assertEquals(LikeTask.STATE_NONE, LikeTask.stateAfter(LikeTask.OPPOSE, 1));
        assertEquals(LikeTask.STATE_NONE, LikeTask.stateAfter(LikeTask.SUPPORT, 0));
    }

    @Test
    public void tappingToggles() {
        assertEquals(LikeTask.STATE_UP, LikeTask.expectedState(LikeTask.STATE_NONE, LikeTask.SUPPORT));
        assertEquals(LikeTask.STATE_NONE, LikeTask.expectedState(LikeTask.STATE_UP, LikeTask.SUPPORT));
        assertEquals(LikeTask.STATE_DOWN, LikeTask.expectedState(LikeTask.STATE_UP, LikeTask.OPPOSE));
        assertEquals(LikeTask.STATE_NONE, LikeTask.expectedState(LikeTask.STATE_DOWN, LikeTask.OPPOSE));
        assertEquals(LikeTask.STATE_UP, LikeTask.expectedState(LikeTask.STATE_DOWN, LikeTask.SUPPORT));
    }
}
