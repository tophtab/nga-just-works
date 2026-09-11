package sp.phone.ui.fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Transient model choices for one settings draft. Contains no endpoint or credentials. */
final class AiModelEditorState {

    enum LoadStatus { LOADING, READY, EMPTY, FAILED }

    private List<String> models = Collections.emptyList();
    private String customModel = "";
    private String selectedModel;
    private LoadStatus loadStatus = LoadStatus.EMPTY;
    private long generation;
    private boolean open;

    long open(String currentModel) {
        close();
        open = true;
        customModel = currentModel;
        selectedModel = models.contains(currentModel) ? currentModel : null;
        loadStatus = LoadStatus.LOADING;
        return generation;
    }

    boolean isActive(long requestGeneration) {
        return open && generation == requestGeneration;
    }

    boolean modelsLoaded(long requestGeneration, List<String> result) {
        if (!isActive(requestGeneration) || loadStatus != LoadStatus.LOADING) {
            return false;
        }
        loadStatus = result.isEmpty() ? LoadStatus.EMPTY : LoadStatus.READY;
        if (!result.isEmpty()) {
            models = Collections.unmodifiableList(new ArrayList<>(result));
        }
        // Discovery only changes the choices. It never changes the current input or selection.
        return true;
    }

    boolean loadFailed(long requestGeneration) {
        if (!isActive(requestGeneration) || loadStatus != LoadStatus.LOADING) {
            return false;
        }
        loadStatus = LoadStatus.FAILED;
        return true;
    }

    List<String> getModels() {
        if (selectedModel == null || models.contains(selectedModel)) {
            return models;
        }
        // A refresh may omit a model selected while loading. Keep that choice visible this time.
        List<String> choices = new ArrayList<>(models);
        choices.add(selectedModel);
        return Collections.unmodifiableList(choices);
    }

    LoadStatus getLoadStatus() {
        return loadStatus;
    }

    void selectModel(String model) {
        selectedModel = model;
    }

    void selectCustom() {
        selectedModel = null;
    }

    boolean isCustom() {
        return selectedModel == null;
    }

    void setCustomModel(String model) {
        customModel = model;
    }

    String getCustomModel() {
        return customModel;
    }

    String getModel() {
        return isCustom() ? customModel : selectedModel;
    }

    void close() {
        generation++;
        open = false;
        customModel = "";
        selectedModel = null;
    }

    void clearModels() {
        close();
        models = Collections.emptyList();
    }
}
