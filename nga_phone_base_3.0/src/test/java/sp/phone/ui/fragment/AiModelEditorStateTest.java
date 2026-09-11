package sp.phone.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class AiModelEditorStateTest {

    @Test
    public void discoveryWithoutAConfiguredModelPreservesTextTypedWhileLoading() {
        AiModelEditorState state = new AiModelEditorState();
        long request = state.open("");
        assertEquals(AiModelEditorState.LoadStatus.LOADING, state.getLoadStatus());
        state.setCustomModel("partially-typed-model");

        assertTrue(state.modelsLoaded(request, Arrays.asList("provider-a", "provider-b")));
        assertTrue(state.isCustom());
        assertEquals("partially-typed-model", state.getModel());
        assertEquals(Arrays.asList("provider-a", "provider-b"), state.getModels());
    }

    @Test
    public void matchingDiscoveryResultDoesNotSwitchAwayFromCustomInput() {
        AiModelEditorState state = new AiModelEditorState();
        long request = state.open("existing-model");
        state.selectCustom();
        state.setCustomModel("provider-model");

        state.modelsLoaded(request, Collections.singletonList("provider-model"));

        assertTrue(state.isCustom());
        assertEquals("provider-model", state.getCustomModel());
        assertEquals("provider-model", state.getModel());
    }

    @Test
    public void refreshKeepsASelectionMadeWhileLoadingVisibleEvenIfProviderOmitsIt() {
        AiModelEditorState state = withCachedModels("old-a", "old-b");
        long refresh = state.open("old-a");
        state.selectModel("old-b");

        state.modelsLoaded(refresh, Collections.singletonList("new-model"));

        assertFalse(state.isCustom());
        assertEquals("old-b", state.getModel());
        assertEquals(Arrays.asList("new-model", "old-b"), state.getModels());
        state.close();
        state.open("old-b");
        assertEquals("Only this editor pins an omitted selection",
                Collections.singletonList("new-model"), state.getModels());
        assertEquals("old-b", state.getModel());
    }

    @Test
    public void customTextSurvivesSelectingAProviderAndSwitchingBackAfterRefresh() {
        AiModelEditorState state = withCachedModels("provider-a");
        long refresh = state.open("initial-model");
        state.setCustomModel("custom draft");
        state.selectModel("provider-a");
        state.modelsLoaded(refresh, Arrays.asList("provider-a", "provider-b"));

        state.selectCustom();

        assertEquals("custom draft", state.getModel());
    }

    @Test
    public void failedRefreshRetainsSameServiceChoicesAndSelection() {
        AiModelEditorState state = withCachedModels("model-a", "model-b");
        long refresh = state.open("model-b");

        assertTrue(state.loadFailed(refresh));

        assertEquals(AiModelEditorState.LoadStatus.FAILED, state.getLoadStatus());
        assertEquals(Arrays.asList("model-a", "model-b"), state.getModels());
        assertEquals("model-b", state.getModel());
        state.selectCustom();
        state.setCustomModel("manual-fallback");
        assertEquals("manual-fallback", state.getModel());
    }

    @Test
    public void emptyRefreshRetainsSameServiceChoicesAndCustomText() {
        AiModelEditorState state = withCachedModels("model-a");
        long refresh = state.open("manual-model");

        assertTrue(state.modelsLoaded(refresh, Collections.emptyList()));

        assertEquals(AiModelEditorState.LoadStatus.EMPTY, state.getLoadStatus());
        assertEquals(Collections.singletonList("model-a"), state.getModels());
        assertTrue(state.isCustom());
        assertEquals("manual-model", state.getModel());
    }

    @Test
    public void firstFailureOrEmptyResultStillAllowsManualEntry() {
        for (boolean failure : new boolean[]{true, false}) {
            AiModelEditorState state = new AiModelEditorState();
            long request = state.open("");
            if (failure) {
                state.loadFailed(request);
            } else {
                state.modelsLoaded(request, Collections.emptyList());
            }

            state.setCustomModel("manual-model");

            assertTrue(state.getModels().isEmpty());
            assertTrue(state.isCustom());
            assertEquals("manual-model", state.getModel());
        }
    }

    @Test
    public void dismissalDiscardsLateResultsBeforeTheyCanEnterTheCache() {
        AiModelEditorState state = new AiModelEditorState();
        long request = state.open("draft-model");
        state.close();

        assertFalse(state.modelsLoaded(request, Collections.singletonList("stale-model")));
        assertFalse(state.loadFailed(request));
        state.open("draft-model");
        assertTrue(state.getModels().isEmpty());
        assertEquals(AiModelEditorState.LoadStatus.LOADING, state.getLoadStatus());
    }

    @Test
    public void reopeningTheSameModelRejectsSuccessAndErrorFromThePreviousEditor() {
        AiModelEditorState state = new AiModelEditorState();
        long previous = state.open("same-model");
        long current = state.open("same-model");
        state.setCustomModel("new draft");

        assertFalse(state.modelsLoaded(previous, Collections.singletonList("stale-model")));
        assertFalse(state.loadFailed(previous));
        assertEquals(AiModelEditorState.LoadStatus.LOADING, state.getLoadStatus());
        assertTrue(state.modelsLoaded(current, Collections.singletonList("current-model")));
        assertEquals(Collections.singletonList("current-model"), state.getModels());
        assertEquals("new draft", state.getModel());
    }

    @Test
    public void changingServiceDropsTheCacheAndInvalidatesItsOutstandingRefresh() {
        AiModelEditorState state = withCachedModels("old-service-model");
        long previous = state.open("old-service-model");
        state.clearModels();
        long current = state.open("new-service-model");

        assertFalse(state.modelsLoaded(previous, Collections.singletonList("late-old-model")));
        state.loadFailed(current);

        assertTrue(state.getModels().isEmpty());
        assertEquals("new-service-model", state.getModel());
    }

    @Test
    public void aDuplicateCallbackCannotReplaceACompletedResult() {
        AiModelEditorState state = new AiModelEditorState();
        long request = state.open("");
        state.modelsLoaded(request, Collections.singletonList("first-model"));

        assertFalse(state.modelsLoaded(request, Collections.singletonList("second-model")));
        assertFalse(state.loadFailed(request));

        assertEquals(AiModelEditorState.LoadStatus.READY, state.getLoadStatus());
        assertEquals(Collections.singletonList("first-model"), state.getModels());
    }

    @Test
    public void cancellingAnEditDiscardsItsTextButRetainsSameServiceChoices() {
        AiModelEditorState state = withCachedModels("provider-model");
        state.open("original-model");
        state.setCustomModel("cancelled-edit");
        state.close();

        assertEquals("", state.getCustomModel());
        state.open("original-model");
        assertEquals("original-model", state.getModel());
        assertEquals(Collections.singletonList("provider-model"), state.getModels());
    }

    private static AiModelEditorState withCachedModels(String... models) {
        AiModelEditorState state = new AiModelEditorState();
        long request = state.open("");
        state.modelsLoaded(request, Arrays.asList(models));
        state.close();
        return state;
    }
}
