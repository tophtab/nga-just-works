package sp.phone.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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
        String stored = new String(record, StandardCharsets.ISO_8859_1);
        assertFalse(stored.contains(expected.getApiKey()));
        assertFalse(stored.contains("private.example.test"));
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

    private static AiConfig config() {
        return new AiConfig("https://private.example.test/custom/v1", "synthetic-private-key", "示例模型");
    }
}
