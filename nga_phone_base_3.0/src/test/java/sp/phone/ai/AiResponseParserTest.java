package sp.phone.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import org.junit.Test;

public class AiResponseParserTest {
    @Test
    public void takesOnlyTheFirstTextChoice() throws Exception {
        String body = "{\"choices\":[{\"message\":{\"content\":\"  第一条总结  \"}},"
                + "{\"message\":{\"content\":\"不会选择这条\"}}]}";
        assertEquals("第一条总结", AiResponseParser.firstText(body));
    }

    @Test
    public void nestedPunctuationInsideTextIsNotCountedAsJsonStructure() throws Exception {
        String content = "引号：\" [ { \\ / ' " + "[".repeat(100);
        assertEquals(content, AiResponseParser.firstText(response(content)));
    }

    @Test
    public void missingNullOrNonTextResultsAreErrors() {
        String[] invalid = {"{}", "[]", "null", "{\"choices\":[]}", "{\"choices\":[null]}",
                "{\"choices\":[{\"message\":null}]}", "{\"choices\":[{\"message\":{\"content\":3}}]}",
                "{\"choices\":[{\"message\":{\"content\":null}}]}", response("   "),
                "{\"choices\":[{\"message\":{\"content\":null}},{\"message\":{\"content\":\"later\"}}]}"};
        for (String body : invalid) {
            assertEquals(AiError.INVALID_RESPONSE, assertThrows(AiResponseParser.InvalidResponseException.class,
                    () -> AiResponseParser.firstText(body)).error);
        }
    }

    @Test
    public void malformedAndTooDeepJsonFailsWithoutLeakingInput() {
        String[] invalid = {"{", "<html>synthetic-private-value</html>", "{} {}",
                "{'choices': []}", "{/* comment */\"choices\":[]}", "{\"value\":\"line\nbreak\"}",
                "{\"ignored\":" + "[".repeat(60) + "0" + "]".repeat(60) + "}"};
        for (String body : invalid) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> SafeJsonParser.parseObject(body));
            assertEquals("响应内容无法解析", error.getMessage());
            assertNull(error.getCause());
            assertFalse(error.toString().contains("synthetic-private-value"));
        }
    }

    @Test
    public void legacyUnquotedFieldNamesRemainCompatibleWithTheTypedResultShape() throws Exception {
        assertEquals(3, SafeJsonParser.parseObject("{unquoted:3}").getIntValue("unquoted"));
        assertEquals("兼容结果", AiResponseParser.firstText("{choices:[{message:{content:\"兼容结果\"}}]}"));
        assertEquals(AiError.INVALID_RESPONSE, assertThrows(AiResponseParser.InvalidResponseException.class,
                () -> AiResponseParser.firstText("{choices:[{message:{content:3}}]}")).error);
    }

    @Test
    public void typeAndReferenceKeysAreOrdinaryData() throws Exception {
        JSONObject value = SafeJsonParser.parseObject("{\"@type\":\"not.a.LoadableClass\",\"$ref\":\"$\","
                + "\"choices\":[{\"message\":{\"content\":\"safe\"}}]}");
        assertEquals("not.a.LoadableClass", value.get("@type"));
        assertEquals("$", value.get("$ref"));
        assertEquals("safe", AiResponseParser.firstText(value.toJSONString()));
    }

    @Test
    public void outputAndSharedParserInputAreBounded() {
        AiResponseParser.InvalidResponseException error = assertThrows(AiResponseParser.InvalidResponseException.class,
                () -> AiResponseParser.firstText(response("x".repeat(AiResponseParser.MAX_TEXT_CHARS + 1))));
        assertEquals(AiError.RESPONSE_TOO_LARGE, error.error);
        assertThrows(IllegalArgumentException.class,
                () -> SafeJsonParser.parseObject("{\"value\":\"" + "x".repeat(512 * 1024) + "\"}"));
    }

    static String response(String content) {
        return "{\"choices\":[{\"message\":{\"content\":" + JSON.toJSONString(content) + "}}]}";
    }
}
