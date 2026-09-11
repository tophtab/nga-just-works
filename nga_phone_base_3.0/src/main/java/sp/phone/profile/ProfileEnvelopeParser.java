package sp.phone.profile;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/** The established USER.PROFILE wrapper repairs, shared by both profile readers. */
public final class ProfileEnvelopeParser {

    public static final class NonProfileResponseException extends IllegalArgumentException {
        private NonProfileResponseException() {
            super("Expected USER.PROFILE envelope");
        }
    }

    private ProfileEnvelopeParser() {
    }

    public static JSONObject parse(String source) {
        if (source == null) {
            return null;
        }
        String json = source.replace("window.script_muti_get_var_store=", "");
        if (json.contains("/*error fill content")) {
            json = json.substring(0, json.indexOf("/*error fill content"))
                    .replaceAll("\"content\":\\+(\\d+),", "\"content\":\"+$1\",");
        }
        json = json.replaceAll("\"subject\":\\+(\\d+),", "\"subject\":\"+$1\",")
                .replace("/*$js$*/", "");
        if (!json.trim().startsWith("{")) {
            throw new NonProfileResponseException();
        }
        return JSON.parseObject(json);
    }
}
