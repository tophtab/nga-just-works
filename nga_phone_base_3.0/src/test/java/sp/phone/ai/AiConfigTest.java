package sp.phone.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class AiConfigTest {
    @Test
    public void existingCallersDefaultToRoastAndExplicitPromptSettingsAreRetained() {
        AiConfig defaultConfig = config("https://api.example.test/v1");
        assertEquals(AiProfilePrompt.DEFAULT, defaultConfig.getProfilePrompt());
        AiProfilePrompt prompt = new AiProfilePrompt(AiProfilePrompt.Style.CUSTOM, "Custom\nPrompt");
        AiConfig custom = new AiConfig(defaultConfig.getEndpoint(), defaultConfig.getApiKey(),
                defaultConfig.getModel(), prompt);
        assertEquals(prompt, custom.getProfilePrompt());
        assertThrows(IllegalArgumentException.class,
                () -> new AiConfig(defaultConfig.getEndpoint(), defaultConfig.getApiKey(),
                        defaultConfig.getModel(), null));
    }

    @Test
    public void normalizesBaseAndCompleteUrlsWithoutGuessingVersion() {
        assertEquals("https://api.example.test/chat/completions", config("https://api.example.test").getEndpoint());
        assertEquals("https://api.example.test/v1/chat/completions", config("https://api.example.test/v1/").getEndpoint());
        assertEquals("https://api.example.test/v1/chat/completions", config("https://api.example.test/v1/chat/completions///").getEndpoint());
        assertEquals("https://api.example.test/custom/v2/chat/completions", config("https://API.example.test:443/custom/v2").getEndpoint());
    }

    @Test
    public void acceptsHttpLanAddressesAndPreservesCustomPrefixesAndPorts() {
        assertEquals("http://192.168.1.10:8080/chat/completions", config("http://192.168.1.10:8080").getEndpoint());
        assertEquals("http://localhost:11434/v1/chat/completions", config("http://localhost:11434/v1/").getEndpoint());
        assertEquals("http://[::1]:8080/custom/v2/chat/completions",
                config("http://[::1]:8080/custom/v2/chat/completions///").getEndpoint());
        assertEquals("http://api.example.test/custom%2Ftenant/v2/chat/completions",
                config(" HTTP://API.example.test:80/custom%2Ftenant/v2/ ").getEndpoint());
    }

    @Test
    public void normalizedConfigurationIsStableAcrossSaveAndLoad() {
        AiConfig first = new AiConfig(" https://api.example.test/v1/ ", " test-key ", " 示例模型 ");
        AiConfig second = new AiConfig(first.getEndpoint(), first.getApiKey(), first.getModel());
        assertEquals(first.getEndpoint(), second.getEndpoint());
        assertEquals("test-key", second.getApiKey());
        assertEquals("示例模型", second.getModel());
    }

    @Test
    public void rejectsOtherSchemesCredentialsQueryFragmentAndAmbiguousUrls() {
        String[] invalid = {
                null, "", "  ", "ftp://api.example.test/v1", "file:///v1",
                "api.example.test/v1", "https:///v1", "https://user:password@api.example.test/v1",
                "https://@api.example.test/v1", "https://api.example.test/v1?key=test-key",
                "https://api.example.test/v1?", "https://api.example.test/v1#fragment",
                "https://api.example.test/v1#", "https://api.example.test/has space",
                "https://api.example.test\\other/v1", "https://api.example.test/v1\n",
                "https://api.example.test:99999/v1"
        };
        for (String value : invalid) {
            assertThrows(IllegalArgumentException.class, () -> config(value));
        }
    }

    @Test
    public void httpKeepsTheSameNonSchemeUrlRestrictions() {
        String[] invalid = {
                "http:///v1", "http://user:password@localhost:8080/v1", "http://@localhost/v1",
                "http://localhost/v1?key=test-key", "http://localhost/v1?", "http://localhost/v1#",
                "http://localhost/v1#fragment", "http://localhost/has space", "http://localhost\\other/v1",
                "http://localhost/v1\n", "http://localhost/v1\u007f", "http://localhost:0/v1",
                "http://localhost:-1/v1", "http://localhost:99999/v1", "http://localhost:invalid/v1"
        };
        for (String value : invalid) {
            assertThrows(IllegalArgumentException.class, () -> config(value));
        }
    }

    @Test
    public void rejectsEmptyOversizedOrUnsafeFields() {
        String[] invalidKeys = {null, "", "   ", "key\r\nCookie: synthetic", "key\t", "key with space", "密钥", "x".repeat(4097)};
        for (String key : invalidKeys) {
            assertThrows(IllegalArgumentException.class,
                    () -> new AiConfig("https://api.example.test/v1", key, "test-model"));
        }
        String[] invalidModels = {null, "", " ", "model\n", "x".repeat(257)};
        for (String model : invalidModels) {
            assertThrows(IllegalArgumentException.class,
                    () -> new AiConfig("https://api.example.test/v1", "test-key", model));
        }
        assertThrows(IllegalArgumentException.class, () -> config("https://api.example.test/" + "x".repeat(2048)));
        assertThrows(IllegalArgumentException.class, () -> config("http://localhost/" + "x".repeat(2048)));
    }

    @Test
    public void errorsAndToStringDoNotExposeConfigurationValues() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> config("https://private.example.test/v1?key=synthetic-private-key"));
        assertFalse(error.toString().contains("private.example.test"));
        assertFalse(error.toString().contains("synthetic-private-key"));
        assertNull(error.getCause());
        AiConfig config = new AiConfig("https://private.example.test/v1", "synthetic-private-key", "private-model");
        assertFalse(config.toString().contains("private"));
    }

    private static AiConfig config(String endpoint) {
        return new AiConfig(endpoint, "test-key", "test-model");
    }
}
