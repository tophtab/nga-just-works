package sp.phone.ai.summary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sp.phone.ai.AiProfilePrompt;

/** Sequential first-page topic/reply samples for one frozen profile UID. */
public final class ProfileSummaryLoader {

    private static final int MAX_EMPTY_RETRIES = 2;

    public enum Kind { TOPICS, REPLIES }

    public static final class Page {
        final String uid;
        final Kind kind;
        final List<ProfileSummaryInput.Entry> entries;

        public Page(String uid, Kind kind, List<ProfileSummaryInput.Entry> entries) {
            this.uid = uid;
            this.kind = kind;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries.subList(
                    0, Math.min(entries.size(), ProfileSummaryInput.MAX_ITEMS_PER_PAGE))));
        }
    }

    public interface PageCallback {
        void onSuccess(Page page);
        void onError(String safeMessage);
    }

    public interface PageSource {
        /** There is intentionally no page argument or pagination result. */
        SummaryController.Cancelable loadFirstPage(String uid, Kind kind, PageCallback callback);
    }

    private final PageSource pages;

    public ProfileSummaryLoader(PageSource pages) {
        this.pages = pages;
    }

    public SummaryController.Cancelable load(String uid, String userName,
                                              SummaryController.Callback callback) {
        return load(uid, userName, AiProfilePrompt.DEFAULT, callback);
    }

    public SummaryController.Cancelable load(String uid, String userName, AiProfilePrompt profilePrompt,
                                              SummaryController.Callback callback) {
        if (uid == null || !uid.matches("[1-9][0-9]{0,18}")) {
            callback.onError("用户资料尚未就绪");
            return SummaryController.Cancelable.NONE;
        }
        Load load = new Load(uid, userName, profilePrompt, callback);
        load.request(Kind.TOPICS, 0);
        return load;
    }

    private final class Load implements SummaryController.Cancelable {
        final String uid;
        final String userName;
        final AiProfilePrompt profilePrompt;
        final SummaryController.Callback callback;
        List<ProfileSummaryInput.Entry> topics;
        Attempt current;
        boolean stopped;

        Load(String uid, String userName, AiProfilePrompt profilePrompt,
             SummaryController.Callback callback) {
            this.uid = uid;
            this.userName = userName;
            this.profilePrompt = profilePrompt;
            this.callback = callback;
        }

        void request(Kind kind, int emptyRetries) {
            final Attempt attempt = new Attempt(kind, emptyRetries);
            synchronized (this) {
                if (stopped) {
                    return;
                }
                current = attempt;
            }
            try {
                // A source may synchronously read/complete; never hold the lifecycle lock here.
                SummaryController.Cancelable call = pages.loadFirstPage(uid, kind, new PageCallback() {
                    @Override
                    public void onSuccess(Page page) {
                        accept(attempt, page);
                    }

                    @Override
                    public void onError(String message) {
                        fail(attempt, message);
                    }
                });
                // A fake or cached source may complete synchronously before returning its handle.
                final boolean stale;
                synchronized (this) {
                    stale = stopped || current != attempt;
                    if (!stale) {
                        attempt.call = call;
                    }
                }
                if (stale) {
                    call.cancel();
                }
            } catch (RuntimeException ignored) {
                // This also owns a synchronous failure when the second read is started from an
                // asynchronous first-page callback, outside SummaryController.start's stack.
                fail(attempt, "无法读取 NGA 内容，请稍后重试");
            }
        }

        void accept(Attempt attempt, Page page) {
            final boolean invalid;
            final boolean retry;
            final List<ProfileSummaryInput.Entry> selectedTopics;
            synchronized (this) {
                if (stopped || current != attempt) {
                    return;
                }
                // Retire the exact attempt before accepting another callback of the same kind.
                current = null;
                invalid = page == null || !uid.equals(page.uid) || attempt.kind != page.kind;
                retry = !invalid && page.entries.isEmpty() && attempt.emptyRetries < MAX_EMPTY_RETRIES;
                selectedTopics = topics;
                if (invalid || (!retry && attempt.kind == Kind.REPLIES)) {
                    stopped = true;
                    topics = null;
                } else if (!retry) {
                    topics = page.entries;
                }
            }
            if (invalid) {
                attempt.call.cancel();
                callback.onError("用户资料已变化，请重新发起总结");
            } else if (retry) {
                request(attempt.kind, attempt.emptyRetries + 1);
            } else if (attempt.kind == Kind.TOPICS) {
                request(Kind.REPLIES, 0);
            } else {
                ProfileSummaryInput input = new ProfileSummaryInput(uid, userName, selectedTopics, page.entries);
                if (input.isEmpty()) {
                    callback.onError("没有可用于总结的近期公开内容");
                } else {
                    callback.onSuccess(input.toPrompt(profilePrompt));
                }
            }
        }

        void fail(Attempt attempt, String message) {
            final SummaryController.Cancelable active;
            synchronized (this) {
                if (stopped || current != attempt) {
                    return;
                }
                stopped = true;
                active = attempt.call;
                current = null;
                topics = null;
            }
            active.cancel();
            callback.onError(message);
        }

        @Override
        public void cancel() {
            final SummaryController.Cancelable active;
            synchronized (this) {
                stopped = true;
                active = current == null ? SummaryController.Cancelable.NONE : current.call;
                current = null;
                topics = null;
            }
            active.cancel();
        }
    }

    private static final class Attempt {
        final Kind kind;
        final int emptyRetries;
        SummaryController.Cancelable call = SummaryController.Cancelable.NONE;

        Attempt(Kind kind, int emptyRetries) {
            this.kind = kind;
            this.emptyRetries = emptyRetries;
        }
    }
}
