package sp.phone.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AiStreamParserTest {
    @Test
    public void splitUtf8MultilineDataAndAllSseLineEndingsAreSupported() throws Exception {
        for (String newline : new String[]{"\n", "\r", "\r\n"}) {
            String stream = "\ufeff: heartbeat" + newline + "id: synthetic-id" + newline
                    + "event: message" + newline + "retry: 100" + newline
                    + "data: {\"choices\":[" + newline
                    + "data: {\"index\":0,\"delta\":{\"content\":\"中文🙂\"}}]}" + newline + newline
                    + "data: [DONE]" + newline + newline;
            for (int fragment : new int[]{1, 2, 7}) {
                assertEquals("中文🙂", AiStreamParser.read(new FragmentedInput(stream, fragment),
                        (answer, reasoning) -> { }));
            }
        }
    }

    @Test
    public void nativeAndInlineThinkingStaySeparateAcrossEveryTagSplit() throws Exception {
        String content = " \n<thinking>inline thought</thinking>\ncomplete answer";
        for (int split = 1; split < content.length(); split++) {
            List<String[]> snapshots = new ArrayList<>();
            String stream = event(chunk(content.substring(0, split), "native thought;", null))
                    + event(chunk(content.substring(split), null, "stop"));
            assertEquals("\ncomplete answer", parse(stream, snapshots));
            String[] last = snapshots.get(snapshots.size() - 1);
            assertEquals("\ncomplete answer", last[0]);
            assertEquals("native thought;inline thought", last[1]);
            for (String[] snapshot : snapshots) {
                assertFalse(snapshot[0].contains("thought"));
                assertFalse(snapshot[0].contains("<thinking"));
            }
        }
    }

    @Test
    public void shortThinkingTagAndLiteralTagsAfterProseAreHandledDifferently() throws Exception {
        String stream = event(chunk("<thi", null, null))
                + event(chunk("nk>synthetic thought</thi", null, null))
                + event(chunk("nk>Answer with <think>literal tags</think>", null, null)) + event("[DONE]");
        List<String[]> snapshots = new ArrayList<>();
        assertEquals("Answer with <think>literal tags</think>", parse(stream, snapshots));
        assertEquals("synthetic thought", snapshots.get(snapshots.size() - 1)[1]);
    }

    @Test
    public void unclosedMatchedTagsStayReasoningEvenWhenTheStreamCompletes() {
        List<String[]> snapshots = new ArrayList<>();
        assertError(AiError.EMPTY_RESPONSE,
                event(chunk("<think>synthetic thought", null, "stop")), snapshots);
        for (String[] snapshot : snapshots) {
            assertEquals("", snapshot[0]);
        }
        assertEquals("synthetic thought", snapshots.get(snapshots.size() - 1)[1]);
    }

    @Test
    public void usageAndNonzeroChoiceEventsNeverOverwriteTheAnswerOrEndTheStream() throws Exception {
        String stream = event("{\"choices\":[{\"index\":1,\"delta\":{\"content\":\"ignored\"},"
                + "\"finish_reason\":\"length\"}]}")
                + event(chunk("first ", null, null))
                + event("{\"choices\":[],\"usage\":{\"completion_tokens\":3}}")
                + event("{\"choices\":[{\"index\":2,\"delta\":{\"content\":\"ignored\"}},"
                + "{\"index\":0,\"delta\":{\"content\":\"answer\"}}]}") + event("[DONE]");
        List<String[]> snapshots = new ArrayList<>();
        assertEquals("first answer", parse(stream, snapshots));
        for (String[] snapshot : snapshots) {
            assertFalse(snapshot[0].contains("ignored"));
            assertFalse(snapshot[0].contains("usage"));
        }
    }

    @Test
    public void explicitZeroChoiceWinsOverAnUnindexedFirstRow() throws Exception {
        String stream = event("{\"choices\":[{\"delta\":{\"content\":\"ignored\"},"
                + "\"finish_reason\":\"length\"},{\"index\":0,\"delta\":{\"content\":\"selected\"}}]}")
                + event("[DONE]");
        assertEquals("selected", parse(stream, new ArrayList<>()));
    }

    @Test
    public void mixedIndexesWithoutZeroNeverBorrowTheUnindexedFirstRow() {
        String stream = event("{\"choices\":[{\"delta\":{\"content\":\"ignored\"},"
                + "\"finish_reason\":\"length\"},{\"index\":1,\"delta\":{}}]}") + event("[DONE]");
        List<String[]> snapshots = new ArrayList<>();
        assertError(AiError.EMPTY_RESPONSE, stream, snapshots);
        for (String[] snapshot : snapshots) {
            assertEquals("", snapshot[0]);
        }
    }

    @Test
    public void usageReasoningAndToolArgumentsAloneCannotBecomeSuccessfulReplies() {
        String[] streams = {event("{\"choices\":[],\"usage\":{\"completion_tokens\":3}}") + event("[DONE]"),
                event(chunk(null, "synthetic thought", "stop")),
                event("{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"function\":{"
                        + "\"arguments\":\"must not be displayed\"}}]},\"finish_reason\":\"tool_calls\"}]}"),
                event(chunk("\u3000\u00a0", null, "stop")), event("[DONE]")};
        for (String stream : streams) {
            List<String[]> snapshots = new ArrayList<>();
            assertError(AiError.EMPTY_RESPONSE, stream, snapshots);
            for (String[] snapshot : snapshots) {
                assertFalse(snapshot[0].contains("thought"));
                assertFalse(snapshot[0].contains("displayed"));
            }
        }
    }

    @Test
    public void lengthAndPrematureEofKeepPartialTextButHaveDifferentErrors() {
        for (boolean length : new boolean[]{true, false}) {
            String stream = event(chunk("partial answer", "synthetic thought", length ? "length" : null));
            List<String[]> snapshots = new ArrayList<>();
            assertError(length ? AiError.OUTPUT_EXHAUSTED : AiError.INTERRUPTED_RESPONSE, stream, snapshots);
            String[] last = snapshots.get(snapshots.size() - 1);
            assertEquals("partial answer", last[0]);
            assertEquals("synthetic thought", last[1]);
        }
        List<String[]> thinking = new ArrayList<>();
        assertError(AiError.INTERRUPTED_RESPONSE,
                event(chunk("<think>synthetic thought</thi", null, null)), thinking);
        assertEquals("synthetic thought</thi", thinking.get(thinking.size() - 1)[1]);
        assertEquals("", thinking.get(thinking.size() - 1)[0]);
    }

    @Test
    public void failureReleasesOrdinaryTextHeldForProtocolScreening() {
        String[] ordinary = {"{ordinary braces}", "{\"answer\":\"synthetic code\"}",
                "data: a regular label", ": a regular label", "<thi"};
        for (String text : ordinary) {
            for (boolean malformed : new boolean[]{false, true}) {
                List<String[]> snapshots = new ArrayList<>();
                String stream = event(chunk(text, "synthetic thought", null))
                        + (malformed ? event("{malformed") : "");
                assertError(malformed ? AiError.INVALID_RESPONSE : AiError.INTERRUPTED_RESPONSE,
                        stream, snapshots);
                String[] last = snapshots.get(snapshots.size() - 1);
                assertEquals(text, last[0]);
                assertEquals("synthetic thought", last[1]);
            }
        }
    }

    @Test
    public void failureDoesNotReleaseRecognizedSerializedEnvelopes() {
        String[] protocol = {AiResponseParserTest.response("nested answer"),
                "data: {\"choices\":[],\"usage\":{\"completion_tokens\":7}}\n\ndata: [DONE]\n\n",
                "{\"object\":\"chat.completion.chunk\",\"choices\":["};
        for (String text : protocol) {
            List<String[]> snapshots = new ArrayList<>();
            assertError(AiError.INTERRUPTED_RESPONSE, event(chunk(text, "synthetic thought", null)), snapshots);
            for (String[] snapshot : snapshots) {
                assertEquals("", snapshot[0]);
            }
            assertEquals("synthetic thought", snapshots.get(snapshots.size() - 1)[1]);
        }
    }

    @Test
    public void finishOrDoneNeedsACompleteSseFrame() {
        List<String[]> snapshots = new ArrayList<>();
        assertError(AiError.INTERRUPTED_RESPONSE,
                event(chunk("partial answer", null, null)) + "data: [DONE]\n", snapshots);
        assertEquals("partial answer", snapshots.get(snapshots.size() - 1)[0]);
        assertError(AiError.INTERRUPTED_RESPONSE, "data: " + chunk("never dispatched", null, "stop"),
                new ArrayList<>());
    }

    @Test
    public void malformedEventsAndServerErrorsPreserveOnlyEarlierTypedProgress() {
        String first = event(chunk("partial answer", "synthetic thought", null));
        String[] malformed = {"{malformed synthetic-private-value", "{\"choices\":[{\"index\":\"0\",\"delta\":{}}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{\"content\":3}}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{\"reasoning\":[]}}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"unknown\"}]}"};
        for (String payload : malformed) {
            List<String[]> snapshots = new ArrayList<>();
            assertError(AiError.INVALID_RESPONSE, first + event(payload), snapshots);
            assertEquals("partial answer", snapshots.get(snapshots.size() - 1)[0]);
        }
        assertError(AiError.SERVER, first + "event: error\ndata: synthetic-private-value\n\n", new ArrayList<>());
        assertError(AiError.SERVER, first + event("{\"error\":{\"message\":\"synthetic-private-value\"}}"),
                new ArrayList<>());
    }

    @Test
    public void serializedProtocolSplitAcrossContentChunksIsNeverPublished() {
        String serialized = "data: {\"choices\":[],\"usage\":{\"completion_tokens\":7}}\n\ndata: [DONE]\n\n";
        for (int split = 1; split < serialized.length(); split++) {
            List<String[]> snapshots = new ArrayList<>();
            String stream = event(chunk(serialized.substring(0, split), null, null))
                    + event(chunk(serialized.substring(split), null, "stop"));
            assertError(AiError.INVALID_RESPONSE, stream, snapshots);
            for (String[] snapshot : snapshots) {
                assertEquals("", snapshot[0]);
            }
        }
        assertError(AiError.INVALID_RESPONSE,
                event(chunk(AiResponseParserTest.response("nested reply"), null, "stop")), new ArrayList<>());
    }

    @Test
    public void invalidAndIncompleteUtf8AreRejectedStrictly() {
        for (byte[] bytes : new byte[][]{{(byte) 0xc3, 0x28}, {(byte) 0xe4, (byte) 0xb8}}) {
            assertThrows(CharacterCodingException.class,
                    () -> AiStreamParser.read(new ByteArrayInputStream(bytes), (answer, reasoning) -> { }));
        }
    }

    @Test
    public void invalidUtf8AfterValidEventsPreservesTheirLatestPartialSnapshot() {
        byte[] prefix = (event(chunk("partial answer", null, null))
                + event(chunk("; final delta", "synthetic thought", null))).getBytes(StandardCharsets.UTF_8);
        for (byte[] invalid : new byte[][]{{(byte) 0xc3, 0x28}, {(byte) 0xe4, (byte) 0xb8}}) {
            byte[] bytes = Arrays.copyOf(prefix, prefix.length + invalid.length);
            System.arraycopy(invalid, 0, bytes, prefix.length, invalid.length);
            List<String[]> snapshots = new ArrayList<>();
            assertThrows(CharacterCodingException.class,
                    () -> AiStreamParser.read(new ByteArrayInputStream(bytes),
                            (answer, reasoning) -> snapshots.add(new String[]{answer, reasoning})));
            String[] last = snapshots.get(snapshots.size() - 1);
            assertEquals("partial answer; final delta", last[0]);
            assertEquals("synthetic thought", last[1]);
        }
    }

    @Test
    public void bothChannelsCanUseTheirFullIndependentBudgetWithoutPromptLengthClipping() throws Exception {
        String answer = "a".repeat(AiResponseParser.MAX_TEXT_CHARS);
        String reasoning = "r".repeat(AiResponseParser.MAX_TEXT_CHARS);
        List<String[]> snapshots = new ArrayList<>();
        assertEquals(answer, parse(event(chunk(null, reasoning, null))
                + event(chunk(answer, null, "stop")), snapshots));
        assertEquals(reasoning, snapshots.get(snapshots.size() - 1)[1]);
    }

    @Test
    public void channelAndFrameOverflowsFailExplicitlyWithoutClippingSuccess() {
        for (boolean reasoning : new boolean[]{true, false}) {
            String full = "x".repeat(AiResponseParser.MAX_TEXT_CHARS);
            List<String[]> snapshots = new ArrayList<>();
            String stream = event(chunk(reasoning ? "" : full, reasoning ? full : "", null))
                    + event(chunk(reasoning ? "" : "extra", reasoning ? "extra" : "", "stop"));
            assertError(AiError.RESPONSE_TOO_LARGE, stream, snapshots);
            assertEquals(full, snapshots.get(snapshots.size() - 1)[reasoning ? 1 : 0]);
        }
        assertError(AiError.RESPONSE_TOO_LARGE, ":" + "x".repeat(AiStreamParser.MAX_EVENT_CHARS) + "\n\n",
                new ArrayList<>());
        assertError(AiError.RESPONSE_TOO_LARGE,
                "data: {\"padding\":" + JSON.toJSONString("x".repeat(AiStreamParser.MAX_EVENT_CHARS)) + "}\n\n",
                new ArrayList<>());
    }

    static String event(String payload) {
        return "data: " + payload + "\n\n";
    }

    static String chunk(String content, String reasoning, String finish) {
        JSONObject delta = new JSONObject();
        if (content != null) {
            delta.put("content", content);
        }
        if (reasoning != null) {
            delta.put("reasoning_content", reasoning);
        }
        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        choice.put("delta", delta);
        if (finish != null) {
            choice.put("finish_reason", finish);
        }
        JSONObject root = new JSONObject();
        root.put("choices", Collections.singletonList(choice));
        return root.toJSONString();
    }

    private static String parse(String stream, List<String[]> snapshots) throws Exception {
        return AiStreamParser.read(new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)),
                (answer, reasoning) -> snapshots.add(new String[]{answer, reasoning}));
    }

    private static void assertError(AiError expected, String stream, List<String[]> snapshots) {
        AiResponseParser.InvalidResponseException failure = assertThrows(AiResponseParser.InvalidResponseException.class,
                () -> parse(stream, snapshots));
        assertEquals(expected, failure.error);
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("synthetic-private-value"));
    }

    private static final class FragmentedInput extends ByteArrayInputStream {
        private final int fragment;

        FragmentedInput(String stream, int fragment) {
            super(stream.getBytes(StandardCharsets.UTF_8));
            this.fragment = fragment;
        }

        @Override
        public synchronized int read(byte[] buffer, int offset, int length) {
            return super.read(buffer, offset, Math.min(length, fragment));
        }

        @Override
        public synchronized int available() {
            return 0;
        }
    }
}
