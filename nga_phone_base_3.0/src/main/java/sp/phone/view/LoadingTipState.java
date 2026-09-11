package sp.phone.view;

import java.util.function.IntSupplier;

/** One view's unfinished initial-loading occasion, independent of Android callbacks. */
final class LoadingTipState {

    private boolean selected;
    private int currentTip;

    int update(boolean loadingVisible, boolean hostResumed, boolean visibleToUser,
               IntSupplier chooseTip) {
        if (!loadingVisible) {
            reset();
            return 0;
        }
        if (!hostResumed || !visibleToUser) {
            return 0;
        }
        if (!selected) {
            currentTip = chooseTip.getAsInt();
            selected = true;
        }
        return currentTip;
    }

    void reset() {
        selected = false;
        currentTip = 0;
    }
}
