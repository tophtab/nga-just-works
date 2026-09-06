package sp.phone.ai.summary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Sequential, first-page-only TOPIC.LIST reads for one frozen profile UID. */
public final class ProfileSummaryLoader {

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
        if (uid == null || !uid.matches("[1-9][0-9]{0,18}")) {
            callback.onError("用户资料尚未就绪");
            return SummaryController.Cancelable.NONE;
        }
        Load load = new Load(uid, userName, callback);
        load.request(Kind.TOPICS);
        return load;
    }

    private final class Load implements SummaryController.Cancelable {
        final String uid;
        final String userName;
        final SummaryController.Callback callback;
        List<ProfileSummaryInput.Entry> topics;
        SummaryController.Cancelable current = SummaryController.Cancelable.NONE;
        Kind expected;
        boolean stopped;

        Load(String uid, String userName, SummaryController.Callback callback) {
            this.uid = uid;
            this.userName = userName;
            this.callback = callback;
        }

        synchronized void request(Kind kind) {
            if (stopped) {
                return;
            }
            expected = kind;
            try {
                SummaryController.Cancelable call = pages.loadFirstPage(uid, kind, new PageCallback() {
                    @Override
                    public void onSuccess(Page page) {
                        accept(kind, page);
                    }

                    @Override
                    public void onError(String message) {
                        fail(kind, message);
                    }
                });
                // A fake or cached source may complete synchronously before returning its handle.
                if (stopped || expected != kind) {
                    call.cancel();
                } else {
                    current = call;
                }
            } catch (RuntimeException ignored) {
                // This also owns a synchronous failure when the second read is started from an
                // asynchronous first-page callback, outside SummaryController.start's stack.
                fail(kind, "无法读取 NGA 内容，请稍后重试");
            }
        }

        synchronized void accept(Kind kind, Page page) {
            if (stopped || expected != kind) {
                return;
            }
            if (page == null || !uid.equals(page.uid) || kind != page.kind) {
                fail(kind, "用户资料已变化，请重新发起总结");
                return;
            }
            current = SummaryController.Cancelable.NONE;
            if (kind == Kind.TOPICS) {
                topics = page.entries;
                request(Kind.REPLIES);
            } else {
                stopped = true;
                ProfileSummaryInput input = new ProfileSummaryInput(uid, userName, topics, page.entries);
                if (input.isEmpty()) {
                    callback.onError("没有可用于总结的近期公开内容");
                } else {
                    callback.onSuccess(input.toPrompt());
                }
            }
        }

        synchronized void fail(Kind kind, String message) {
            if (!stopped && expected == kind) {
                stopped = true;
                current.cancel();
                current = SummaryController.Cancelable.NONE;
                callback.onError(message);
            }
        }

        @Override
        public synchronized void cancel() {
            stopped = true;
            current.cancel();
            current = SummaryController.Cancelable.NONE;
            topics = null;
        }
    }
}
