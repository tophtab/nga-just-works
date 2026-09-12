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

import sp.phone.ai.AiProfilePrompt;

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
    public void selectedCustomInstructionsAreUsedAfterBothPagesFinish() {
        FakePages pages = new FakePages();
        Result result = new Result();
        String customText = "  Custom style\nSecond line\n";
        AiProfilePrompt prompt = new AiProfilePrompt(AiProfilePrompt.Style.CUSTOM, customText);
        new ProfileSummaryLoader(pages).load("42", "Viewed user", prompt, result);
        pages.requests.get(0).succeed("Topic", "");
        assertNull(result.prompt);
        pages.requests.get(1).succeed("Reply topic", "Public reply");
        assertTrue(result.prompt.contains("输出要求：\n" + customText + "\n"));
        assertTrue(result.prompt.contains("[回复1] Reply topic"));
        assertFalse(result.prompt.contains(AiProfilePrompt.DEFAULT.getInstructions()));
        assertNull(result.error);
    }

    @Test
    public void exhaustedEmptyRetriesStillAllowVisibleActivityFromTheOtherKind() {
        for (ProfileSummaryLoader.Kind emptyKind : ProfileSummaryLoader.Kind.values()) {
            FakePages pages = new FakePages();
            Result result = new Result();
            new ProfileSummaryLoader(pages).load("42", "Viewed user", result);
            if (emptyKind == ProfileSummaryLoader.Kind.TOPICS) {
                for (int i = 0; i < 3; i++) {
                    assertEquals(ProfileSummaryLoader.Kind.TOPICS, pages.requests.get(i).kind);
                    pages.requests.get(i).empty();
                }
            } else {
                pages.requests.get(0).succeed("Visible topic", "");
            }
            assertNull(result.prompt);
            if (emptyKind == ProfileSummaryLoader.Kind.REPLIES) {
                for (int i = 1; i < 4; i++) {
                    assertEquals(ProfileSummaryLoader.Kind.REPLIES, pages.requests.get(i).kind);
                    pages.requests.get(i).empty();
                }
            } else {
                pages.requests.get(3).succeed("Reply topic", "Visible reply");
            }
            assertEquals(4, pages.requests.size());
            assertEquals(1, result.successes);
            assertNull(result.error);
            assertTrue(result.prompt.contains(emptyKind == ProfileSummaryLoader.Kind.TOPICS
                    ? "无可见主题" : "无可见回复"));
            assertTrue(result.prompt.contains(emptyKind == ProfileSummaryLoader.Kind.TOPICS
                    ? "Visible reply" : "Visible topic"));
        }
    }

    @Test
    public void eitherKindCanRecoverOnItsFirstOrSecondEmptyRetry() {
        for (ProfileSummaryLoader.Kind emptyKind : ProfileSummaryLoader.Kind.values()) {
            for (int empties = 1; empties <= 2; empties++) {
                FakePages pages = new FakePages();
                Result result = new Result();
                new ProfileSummaryLoader(pages).load("42", "Viewed user", result);
                int next = 0;
                if (emptyKind == ProfileSummaryLoader.Kind.REPLIES) {
                    pages.requests.get(next++).succeed("Topic", "");
                }
                for (int i = 0; i < empties; i++) {
                    Request request = pages.requests.get(next++);
                    assertEquals(emptyKind, request.kind);
                    request.empty();
                    assertNull(result.prompt);
                }
                pages.requests.get(next++).succeed("Recovered topic", "Recovered reply");
                if (emptyKind == ProfileSummaryLoader.Kind.TOPICS) {
                    pages.requests.get(next).succeed("Reply topic", "Visible reply");
                }
                assertEquals(2 + empties, pages.requests.size());
                assertEquals(1, result.successes);
                assertEquals(0, result.errors);
                assertTrue(result.prompt.contains("Recovered topic"));
                assertTrue(result.prompt.contains("样本数量：主题 1 条，回复 1 条"));
            }
        }
    }

    @Test
    public void bothEmptyKindsStopAfterSixReadsWithoutAProfilePrompt() {
        FakePages pages = new FakePages();
        Result result = new Result();
        new ProfileSummaryLoader(pages).load("42", "Viewed user", result);
        for (int i = 0; i < 6; i++) {
            assertEquals(i < 3 ? ProfileSummaryLoader.Kind.TOPICS : ProfileSummaryLoader.Kind.REPLIES,
                    pages.requests.get(i).kind);
            pages.requests.get(i).empty();
            if (i < 5) {
                assertEquals(0, result.errors);
            }
        }
        assertEquals(6, pages.requests.size());
        assertEquals(1, result.errors);
        assertEquals(0, result.successes);
        assertEquals("没有可用于总结的近期公开内容", result.error);
        assertNull(result.prompt);
    }

    @Test
    public void anErrorAfterAnEmptyResultIsTerminalForEitherKind() {
        for (ProfileSummaryLoader.Kind emptyKind : ProfileSummaryLoader.Kind.values()) {
            FakePages pages = new FakePages();
            Result result = new Result();
            new ProfileSummaryLoader(pages).load("42", "User", result);
            int index = 0;
            if (emptyKind == ProfileSummaryLoader.Kind.REPLIES) {
                pages.requests.get(index++).succeed("Topic", "");
            }
            pages.requests.get(index++).empty();
            Request retry = pages.requests.get(index);
            retry.callback.onError("Safe access failure");
            retry.empty();
            retry.succeed("LATE_TOPIC", "LATE_REPLY");
            assertEquals(index + 1, pages.requests.size());
            assertEquals(1, result.errors);
            assertEquals(0, result.successes);
            assertEquals("Safe access failure", result.error);
            assertNull(result.prompt);
        }
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
    public void staleSameKindCallbacksCannotConsumeRetriesOrReplaceTheirResult() {
        FakePages pages = new FakePages();
        Result result = new Result();
        new ProfileSummaryLoader(pages).load("42", "User", result);
        for (ProfileSummaryLoader.Kind kind : ProfileSummaryLoader.Kind.values()) {
            int first = kind == ProfileSummaryLoader.Kind.TOPICS ? 0 : 3;
            Request original = pages.requests.get(first);
            original.empty();
            Request retry = pages.requests.get(first + 1);
            original.empty();
            original.succeed("STALE_TOPIC", "STALE_REPLY");
            original.callback.onError("STALE_ERROR");
            assertEquals(first + 2, pages.requests.size());
            retry.empty();
            retry.empty();
            retry.callback.onError("STALE_ERROR");
            assertEquals(first + 3, pages.requests.size());
            pages.requests.get(first + 2).succeed("Accepted topic", "Accepted reply");
        }
        assertEquals(6, pages.requests.size());
        assertEquals(1, result.successes);
        assertEquals(0, result.errors);
        assertFalse(result.prompt.contains("STALE"));
    }

    @Test
    public void aSynchronousEmptyAttemptCannotOverwriteTheRetryCancelHandle() {
        for (ProfileSummaryLoader.Kind emptyKind : ProfileSummaryLoader.Kind.values()) {
            List<Request> requests = new ArrayList<>();
            ProfileSummaryLoader loader = new ProfileSummaryLoader((uid, kind, callback) -> {
                Request request = new Request(uid, kind, callback);
                requests.add(request);
                if (kind == ProfileSummaryLoader.Kind.TOPICS && emptyKind == ProfileSummaryLoader.Kind.REPLIES) {
                    request.succeed("Topic", "");
                } else if (requests.size() == (emptyKind == ProfileSummaryLoader.Kind.TOPICS ? 1 : 2)) {
                    request.empty();
                }
                return request;
            });
            Result result = new Result();
            SummaryController.Cancelable load = loader.load("42", "User", result);
            Request retry = requests.get(requests.size() - 1);
            assertEquals(emptyKind, retry.kind);
            assertFalse(retry.canceled);
            assertTrue(requests.get(requests.size() - 2).canceled);
            int count = requests.size();
            load.cancel();
            assertTrue(retry.canceled);
            retry.empty();
            retry.callback.onError("LATE_ERROR");
            assertEquals(count, requests.size());
            assertEquals(0, result.errors);
            assertEquals(0, result.successes);
        }
    }

    @Test
    public void anExceptionFromARetiredSynchronousAttemptCannotFailTheNewRetry() {
        List<Request> requests = new ArrayList<>();
        ProfileSummaryLoader loader = new ProfileSummaryLoader((uid, kind, callback) -> {
            Request request = new Request(uid, kind, callback);
            requests.add(request);
            if (requests.size() == 1) {
                request.empty();
                throw new IllegalStateException("RETIRED_EXCEPTION_SENTINEL");
            }
            return request;
        });
        Result result = new Result();
        loader.load("42", "User", result);
        assertEquals(2, requests.size());
        assertEquals(0, result.errors);
        requests.get(1).succeed("Topic", "");
        requests.get(2).succeed("Topic", "Reply");
        assertEquals(1, result.successes);
        assertEquals(0, result.errors);
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
        assertEquals(6, requests.size());
        assertNotNull(result.error);
        assertNull(result.prompt);
        for (Request request : requests) {
            assertTrue(request.canceled);
        }
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

        void empty() {
            callback.onSuccess(new ProfileSummaryLoader.Page(uid, kind, Collections.emptyList()));
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
        int errors;

        @Override
        public void onSuccess(String text) {
            successes++;
            prompt = text;
        }

        @Override
        public void onError(String safeMessage) {
            errors++;
            error = safeMessage;
        }
    }
}
