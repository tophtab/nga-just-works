package sp.phone.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class AiConfigRecordTest {
    @Test
    public void realAesGcmRoundTripKeepsAllFieldsTogether() throws Exception {
        AiConfig expected = config();
        SecretKey key = newKey();
        byte[] record = AiConfigRecord.encrypt(expected, key);
        AiConfig actual = AiConfigRecord.decrypt(record, key);
        assertEquals(expected.getEndpoint(), actual.getEndpoint());
        assertEquals(expected.getApiKey(), actual.getApiKey());
        assertEquals(expected.getModel(), actual.getModel());
        assertEquals(expected.getProfilePrompt(), actual.getProfilePrompt());
        assertEquals(2, record[4]);
        String stored = new String(record, StandardCharsets.ISO_8859_1);
        assertFalse(stored.contains(expected.getApiKey()));
        assertFalse(stored.contains("private.example.test"));
    }

    @Test
    public void legacyThreeFieldRecordLoadsWithTheNewRoastDefault() throws Exception {
        SecretKey key = newKey();
        AiConfig original = config();
        AiConfig loaded = AiConfigRecord.decrypt(legacyRecord(original, key), key);
        assertEquals(original.getEndpoint(), loaded.getEndpoint());
        assertEquals(original.getApiKey(), loaded.getApiKey());
        assertEquals(original.getModel(), loaded.getModel());
        assertEquals(AiProfilePrompt.DEFAULT, loaded.getProfilePrompt());
        assertEquals(2, AiConfigRecord.encrypt(loaded, key)[4]);
    }

    @Test
    public void everyStyleRoundTripsWithTheExactRetainedMultilineCustomText() throws Exception {
        SecretKey key = newKey();
        String customText = "  第一行 😀\r\n\tSecond line\n\n";
        for (AiProfilePrompt.Style style : AiProfilePrompt.Style.values()) {
            AiProfilePrompt prompt = new AiProfilePrompt(style, customText);
            AiConfig expected = new AiConfig(config().getEndpoint(), config().getApiKey(),
                    config().getModel(), prompt);
            AiConfig loaded = AiConfigRecord.decrypt(AiConfigRecord.encrypt(expected, key), key);
            assertEquals(prompt, loaded.getProfilePrompt());
            assertEquals(customText, loaded.getProfilePrompt().getCustomText());
            assertEquals(prompt.getInstructions(), loaded.getProfilePrompt().getInstructions());
        }
    }

    @Test
    public void maximumLengthMultibyteCustomPromptFitsTheVersionTwoBound() throws Exception {
        SecretKey key = newKey();
        String text = "中".repeat(AiProfilePrompt.MAX_CUSTOM_PROMPT_CHARS);
        AiConfig expected = new AiConfig("https://example.test/" + "x".repeat(1990),
                "k".repeat(4096), "模".repeat(256),
                new AiProfilePrompt(AiProfilePrompt.Style.CUSTOM, text));
        byte[] record = AiConfigRecord.encrypt(expected, key);
        assertTrue(record.length > 16 * 1024);
        assertTrue(record.length <= AiConfigRecord.MAX_RECORD_BYTES);
        AiConfig loaded = AiConfigRecord.decrypt(record, key);
        assertEquals(expected.getEndpoint(), loaded.getEndpoint());
        assertEquals(expected.getApiKey(), loaded.getApiKey());
        assertEquals(expected.getModel(), loaded.getModel());
        assertEquals(expected.getProfilePrompt(), loaded.getProfilePrompt());
    }

    @Test
    public void authenticatedMalformedVersionsAndPromptFieldsFailClosed() throws Exception {
        SecretKey key = newKey();
        AiConfig config = config();
        for (String[] promptFields : new String[][]{
                {}, {"unknown", "Text"}, {"custom", "\u3000\n"},
                {"custom", "x".repeat(8193)}, {"detailed", "x".repeat(8193)},
                {"custom", "Text", "Unexpected field"}}) {
            String[] fields = new String[3 + promptFields.length];
            fields[0] = config.getEndpoint();
            fields[1] = config.getApiKey();
            fields[2] = config.getModel();
            System.arraycopy(promptFields, 0, fields, 3, promptFields.length);
            byte[] record = recordWithFields(2, key, fields);
            assertThrows(Exception.class, () -> AiConfigRecord.decrypt(record, key));
        }
        byte[] unknownVersion = recordWithFields(3, key,
                config.getEndpoint(), config.getApiKey(), config.getModel());
        assertThrows(Exception.class, () -> AiConfigRecord.decrypt(unknownVersion, key));
        byte[] oversizedLegacy = recordWithFields(1, key,
                config.getEndpoint(), config.getApiKey(), config.getModel(), "中".repeat(8192));
        assertThrows(Exception.class, () -> AiConfigRecord.decrypt(oversizedLegacy, key));
        byte[] downgrade = AiConfigRecord.encrypt(config, key);
        downgrade[4] = 1;
        assertThrows(Exception.class, () -> AiConfigRecord.decrypt(downgrade, key));
    }

    @Test
    public void eachEncryptionUsesANewNonce() throws Exception {
        SecretKey key = newKey();
        byte[] first = AiConfigRecord.encrypt(config(), key);
        byte[] second = AiConfigRecord.encrypt(config(), key);
        assertFalse(Arrays.equals(first, second));
        assertFalse(Arrays.equals(Arrays.copyOfRange(first, 5, 17), Arrays.copyOfRange(second, 5, 17)));
        assertEquals(config().getApiKey(), AiConfigRecord.decrypt(first, key).getApiKey());
        assertEquals(config().getApiKey(), AiConfigRecord.decrypt(second, key).getApiKey());
    }

    @Test
    public void versionNonceCiphertextAndTagTamperingFailClosed() throws Exception {
        SecretKey key = newKey();
        byte[] record = AiConfigRecord.encrypt(config(), key);
        for (int offset : new int[]{0, 4, 5, 17, record.length - 1}) {
            byte[] changed = record.clone();
            changed[offset] ^= 1;
            assertThrows(Exception.class, () -> AiConfigRecord.decrypt(changed, key));
        }
        assertThrows(Exception.class, () -> AiConfigRecord.decrypt(record, newKey()));
    }

    @Test
    public void rejectsTruncatedExtendedAndOversizedRecords() throws Exception {
        SecretKey key = newKey();
        byte[] record = AiConfigRecord.encrypt(config(), key);
        for (byte[] invalid : new byte[][]{null, new byte[0], Arrays.copyOf(record, 10),
                Arrays.copyOf(record, record.length - 1), Arrays.copyOf(record, record.length + 1),
                new byte[AiConfigRecord.MAX_RECORD_BYTES + 1]}) {
            assertThrows(Exception.class, () -> AiConfigRecord.decrypt(invalid, key));
        }
    }

    static SecretKey newKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    static byte[] legacyRecord(AiConfig config, SecretKey key) throws Exception {
        return recordWithFields(1, key, config.getEndpoint(), config.getApiKey(), config.getModel());
    }

    private static byte[] recordWithFields(int version, SecretKey key, String... fields) throws Exception {
        byte[] header = {'N', 'G', 'A', 'I', (byte) version};
        ByteArrayOutputStream plaintext = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(plaintext)) {
            for (String field : fields) {
                output.writeUTF(field);
            }
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        cipher.updateAAD(header);
        ByteArrayOutputStream record = new ByteArrayOutputStream();
        record.write(header);
        record.write(cipher.getIV());
        record.write(cipher.doFinal(plaintext.toByteArray()));
        return record.toByteArray();
    }

    private static AiConfig config() {
        return new AiConfig("https://private.example.test/custom/v1", "synthetic-private-key", "示例模型");
    }
}
