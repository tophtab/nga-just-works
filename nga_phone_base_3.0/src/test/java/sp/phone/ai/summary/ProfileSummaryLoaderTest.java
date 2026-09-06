package sp.phone.ai.summary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class ProfileSummaryLoaderTest {

    @Test
    public void twoSequentialFirstPageReadsStayBoundToTheViewedUid() {
        FakePages pages = new FakePages();
        Result result = new Result();
        new ProfileSummaryLoader(pages).load("4200", "Viewed user", result);
        assertEquals(1, pages.requests.size());
        assertEquals("4200", pages.requests.get(0).uid);
        assertEquals(ProfileSummaryLoader.Kind.TOPICS, pages.requests.get(0).kind);
        pages.requests.get(0).succeed("Topic first page", "");
        assertNull(result.prompt);
        assertEquals(2, pages.requests.size());
        assertEquals("4200", pages.requests.get(1).uid);
        assertEquals(ProfileSummaryLoader.Kind.REPLIES, pages.requests.get(1).kind);
        pages.requests.get(1).succeed("Reply topic", "Actual viewed-user reply");
        assertEquals(2, pages.requests.size());
        assertTrue(result.prompt.contains("当前资料页 UID：4200"));
        assertTrue(result.prompt.contains("Topic first page"));
        assertTrue(result.prompt.contains("Actual viewed-user reply"));
        assertNull(result.error);
    }

    @Test
    public void cancellationBeforeFirstPagePreventsReplyFetchEvenIfCallbackArrives() {
        FakePages pages = new FakePages();
        Result result = new Result();
        SummaryController.Cancelable load = new ProfileSummaryLoader(pages).load("42", "User", result);
        load.cancel();
        pages.requests.get(0).succeed("LATE_TOPIC", "");
        assertTrue(pages.requests.get(0).canceled);
        assertEquals(1, pages.requests.size());
        assertNull(result.prompt);
        assertNull(result.error);
    }

    @Test
    public void cancellationDuringReplyReadDiscardsItsResultAndFailure() {
        FakePages pages = new FakePages();
        Result result = new Result();
        SummaryController.Cancelable load = new ProfileSummaryLoader(pages).load("42", "User", result);
        pages.requests.get(0).succeed("Topic", "");
        load.cancel();
        pages.requests.get(1).succeed("Topic", "LATE_REPLY");
        pages.requests.get(1).callback.onError("LATE_FAILURE");
        assertTrue(pages.requests.get(1).canceled);
        assertNull(result.prompt);
        assertNull(result.error);
    }

    @Test
    public void aDifferentUidOrWrongPageKindFailsWithoutUsingItsContent() {
        FakePages pages = new FakePages();
        Result result = new Result();
        new ProfileSummaryLoader(pages).load("42", "User", result);
        pages.requests.get(0).callback.onSuccess(new ProfileSummaryLoader.Page("99",
                ProfileSummaryLoader.Kind.TOPICS, Collections.singletonList(entry("WRONG_USER", ""))));
        assertNull(result.prompt);
        assertNotNull(result.error);
        assertFalse(result.error.contains("WRONG_USER"));
        assertEquals(1, pages.requests.size());

        FakePages wrongKind = new FakePages();
        Result wrongKindResult = new Result();
        new ProfileSummaryLoader(wrongKind).load("42", "User", wrongKindResult);
        wrongKind.requests.get(0).callback.onSuccess(new ProfileSummaryLoader.Page("42",
                ProfileSummaryLoader.Kind.REPLIES, Collections.emptyList()));
        assertNotNull(wrongKindResult.error);
        assertEquals(1, wrongKind.requests.size());
    }

    @Test
    public void firstPageFailureStopsWithoutAutomaticRetryOrPartialSummary() {
        FakePages pages = new FakePages();
        Result result = new Result();
        new ProfileSummaryLoader(pages).load("42", "User", result);
        pages.requests.get(0).callback.onError("Safe access failure");
        pages.requests.get(0).succeed("LATE_TOPIC", "");
        assertEquals("Safe access failure", result.error);
        assertNull(result.prompt);
        assertEquals(1, pages.requests.size());
    }

    @Test
    public void secondReadSynchronousExceptionIsReportedWithoutLeakingItsMessage() {
        FakePages pages = new FakePages();
        pages.throwOnReplies = true;
        Result result = new Result();
        new ProfileSummaryLoader(pages).load("42", "User", result);
        pages.requests.get(0).succeed("Topic", "");
        assertNull(result.prompt);
        assertNotNull(result.error);
        assertFalse(result.error.contains("RAW_RESPONSE_SENTINEL"));
    }

    @Test
    public void duplicateFirstPageCallbacksCannotStartMoreReads() {
        FakePages pages = new FakePages();
        Result result = new Result();
        new ProfileSummaryLoader(pages).load("42", "User", result);
        pages.requests.get(0).succeed("Topic", "");
        pages.requests.get(0).succeed("DUPLICATE_TOPIC", "");
        assertEquals(2, pages.requests.size());
        pages.requests.get(1).succeed("Topic", "Reply");
        pages.requests.get(1).succeed("Topic", "DUPLICATE_REPLY");
        assertEquals(1, result.successes);
        assertFalse(result.prompt.contains("DUPLICATE"));
    }

    @Test
    public void synchronousSourcesAndEmptyActivityFinishWithoutHanging() {
        List<Request> requests = new ArrayList<>();
        ProfileSummaryLoader loader = new ProfileSummaryLoader((uid, kind, callback) -> {
            Request request = new Request(uid, kind, callback);
            requests.add(request);
            callback.onSuccess(new ProfileSummaryLoader.Page(uid, kind, Collections.emptyList()));
            return request;
        });
        Result result = new Result();
        loader.load("42", "User", result);
        assertEquals(2, requests.size());
        assertNotNull(result.error);
        assertNull(result.prompt);
        assertTrue(requests.get(0).canceled);
        assertTrue(requests.get(1).canceled);
    }

    @Test
    public void invalidUidNeverCreatesARead() {
        FakePages pages = new FakePages();
        Result result = new Result();
        new ProfileSummaryLoader(pages).load("42&authorid=99", "User", result);
        assertTrue(pages.requests.isEmpty());
        assertNotNull(result.error);
    }

    private static ProfileSummaryInput.Entry entry(String title, String reply) {
        return new ProfileSummaryInput.Entry(title, "Synthetic board", "2026-01-01", reply);
    }

    private static final class FakePages implements ProfileSummaryLoader.PageSource {
        final List<Request> requests = new ArrayList<>();
        boolean throwOnReplies;

        @Override
        public SummaryController.Cancelable loadFirstPage(String uid, ProfileSummaryLoader.Kind kind,
                                                          ProfileSummaryLoader.PageCallback callback) {
            if (throwOnReplies && kind == ProfileSummaryLoader.Kind.REPLIES) {
                throw new IllegalStateException("RAW_RESPONSE_SENTINEL");
            }
            Request request = new Request(uid, kind, callback);
            requests.add(request);
            return request;
        }
    }

    private static final class Request implements SummaryController.Cancelable {
        final String uid;
        final ProfileSummaryLoader.Kind kind;
        final ProfileSummaryLoader.PageCallback callback;
        boolean canceled;

        Request(String uid, ProfileSummaryLoader.Kind kind, ProfileSummaryLoader.PageCallback callback) {
            this.uid = uid;
            this.kind = kind;
            this.callback = callback;
        }

        void succeed(String title, String reply) {
            callback.onSuccess(new ProfileSummaryLoader.Page(uid, kind,
                    Collections.singletonList(entry(title, reply))));
        }

        @Override
        public void cancel() {
            canceled = true;
        }
    }

    private static final class Result implements SummaryController.Callback {
        String prompt;
        String error;
        int successes;

        @Override
        public void onSuccess(String text) {
            successes++;
            prompt = text;
        }

        @Override
        public void onError(String safeMessage) {
            error = safeMessage;
        }
    }
}
