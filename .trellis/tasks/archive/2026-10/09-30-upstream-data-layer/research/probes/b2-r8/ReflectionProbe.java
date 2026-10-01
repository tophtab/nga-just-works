import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONReader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Names arrive only in an external file; shrinking cannot infer reflected members. */
public final class ReflectionProbe {
    public static void main(String[] args) throws Exception {
        JSONArray cases = JSON.parseArray(Files.readString(Path.of(args[0])));
        for (Object item : cases) {
            JSONObject c = (JSONObject) item;
            Class<?> type = Class.forName(c.getString("class"));
            Object value = JSON.parseObject(c.getJSONObject("input").toJSONString(),
                    type, JSONReader.Feature.SupportSmartMatch);
            JSONObject calls = c.getJSONObject("calls");
            if (calls != null) for (String method : calls.keySet()) {
                Object actual = type.getMethod(method).invoke(value);
                if (!java.util.Objects.equals(calls.get(method), actual))
                    throw new AssertionError(method + ": " + actual);
            }
            JSONObject stringCalls = c.getJSONObject("stringCalls");
            if (stringCalls != null) for (String method : stringCalls.keySet()) {
                JSONArray invocation = stringCalls.getJSONArray(method);
                Object actual = type.getMethod(method, String.class).invoke(value, invocation.getString(0));
                if (!java.util.Objects.equals(invocation.get(1), actual)) throw new AssertionError(method);
            }
            JSONObject encoded = JSON.parseObject(JSON.toJSONString(value));
            subset(c.getJSONObject("expected"), encoded);
            JSONArray absent = c.getJSONArray("absent");
            if (absent != null) for (Object name : absent) {
                if (encoded.containsKey(name)) throw new AssertionError("Leaked " + name);
            }
            System.out.println("PASS " + type.getName() + " " + encoded);
        }
    }
    private static void subset(JSONObject expected, JSONObject actual) {
        for (String key : expected.keySet()) {
            Object e = expected.get(key), a = actual.get(key);
            if (e instanceof JSONObject) subset((JSONObject)e, (JSONObject)a);
            else if (!java.util.Objects.equals(e, a)) throw new AssertionError(key + ": " + e + " != " + a);
        }
    }
}
