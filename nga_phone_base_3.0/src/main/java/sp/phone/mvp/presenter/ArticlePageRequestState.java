package sp.phone.mvp.presenter;

/**
 * Android-free request state for one article page.
 */
final class ArticlePageRequestState {

    enum State {
        IDLE,
        PREFETCHING,
        FOREGROUND_LOADING,
        READY
    }

    enum ForegroundLoadDecision {
        START,
        WAIT_FOR_PREFETCH,
        SHOW_READY_DATA,
        NONE
    }

    private State state = State.IDLE;

    private boolean prefetchPromotedToForeground;

    boolean beginPrefetch() {
        if (state != State.IDLE) {
            return false;
        }
        state = State.PREFETCHING;
        prefetchPromotedToForeground = false;
        return true;
    }

    ForegroundLoadDecision requestForegroundLoad(boolean explicitRefresh) {
        if (state == State.PREFETCHING) {
            prefetchPromotedToForeground = true;
            return ForegroundLoadDecision.WAIT_FOR_PREFETCH;
        }
        if (state == State.FOREGROUND_LOADING) {
            return ForegroundLoadDecision.NONE;
        }
        if (!explicitRefresh && state == State.READY) {
            return ForegroundLoadDecision.SHOW_READY_DATA;
        }
        state = State.FOREGROUND_LOADING;
        prefetchPromotedToForeground = false;
        return ForegroundLoadDecision.START;
    }

    boolean completePrefetch() {
        if (state != State.PREFETCHING) {
            return false;
        }
        boolean wasPromoted = prefetchPromotedToForeground;
        state = State.READY;
        prefetchPromotedToForeground = false;
        return wasPromoted;
    }

    boolean failPrefetch() {
        if (state != State.PREFETCHING) {
            return false;
        }
        boolean wasPromoted = prefetchPromotedToForeground;
        state = State.IDLE;
        prefetchPromotedToForeground = false;
        return wasPromoted;
    }

    boolean movePrefetchToBackground() {
        if (state != State.PREFETCHING) {
            return false;
        }
        boolean wasPromoted = prefetchPromotedToForeground;
        prefetchPromotedToForeground = false;
        return wasPromoted;
    }

    void completeForegroundLoad() {
        if (state == State.FOREGROUND_LOADING) {
            state = State.READY;
        }
    }

    void failForegroundLoad(boolean hasData) {
        if (state == State.FOREGROUND_LOADING) {
            state = hasData ? State.READY : State.IDLE;
        }
    }

    void reset() {
        state = State.IDLE;
        prefetchPromotedToForeground = false;
    }

    State getState() {
        return state;
    }

    boolean isPrefetchPromotedToForeground() {
        return prefetchPromotedToForeground;
    }
}
