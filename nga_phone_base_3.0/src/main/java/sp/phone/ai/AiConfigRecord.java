package sp.phone.ai;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Versioned, authenticated record codec; also exercised with real AES-GCM on the JVM. */
final class AiConfigRecord {
    // Modified UTF may use three bytes per UTF-16 unit, including the 8 Ki custom prompt.
    static final int MAX_RECORD_BYTES = 48 * 1024;
    private static final int MAX_LEGACY_RECORD_BYTES = 16 * 1024;
    private static final byte[] HEADER = {'N', 'G', 'A', 'I', 2};
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private AiConfigRecord() {
    }

    static byte[] encrypt(AiConfig config, SecretKey key) throws GeneralSecurityException, IOException {
        byte[] plaintext = serialize(config);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            // Let the provider create the nonce: Android Keystore enforces randomized encryption.
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.updateAAD(HEADER);
            byte[] nonce = cipher.getIV();
            if (nonce == null || nonce.length != NONCE_BYTES) {
                throw new GeneralSecurityException("Invalid configuration nonce");
            }
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] record = new byte[HEADER.length + NONCE_BYTES + ciphertext.length];
            System.arraycopy(HEADER, 0, record, 0, HEADER.length);
            System.arraycopy(nonce, 0, record, HEADER.length, NONCE_BYTES);
            System.arraycopy(ciphertext, 0, record, HEADER.length + NONCE_BYTES, ciphertext.length);
            if (record.length > MAX_RECORD_BYTES) {
                throw new IOException("Configuration record exceeds limit");
            }
            return record;
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    static AiConfig decrypt(byte[] record, SecretKey key) throws GeneralSecurityException, IOException {
        if (record == null || record.length < HEADER.length + NONCE_BYTES + TAG_BITS / 8
                || record.length > MAX_RECORD_BYTES) {
            throw new IOException("Invalid configuration record");
        }
        for (int i = 0; i < HEADER.length - 1; i++) {
            if (record[i] != HEADER[i]) {
                throw new IOException("Unsupported configuration record");
            }
        }
        int version = record[HEADER.length - 1];
        if (version != 1 && version != 2) {
            throw new IOException("Unsupported configuration record");
        }
        if (version == 1 && record.length > MAX_LEGACY_RECORD_BYTES) {
            throw new IOException("Invalid configuration record");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_BITS, record, HEADER.length, NONCE_BYTES));
        cipher.updateAAD(record, 0, HEADER.length);
        byte[] plaintext = cipher.doFinal(record, HEADER.length + NONCE_BYTES,
                record.length - HEADER.length - NONCE_BYTES);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(plaintext))) {
            String endpoint = input.readUTF();
            String apiKey = input.readUTF();
            String model = input.readUTF();
            AiProfilePrompt prompt = version == 1 ? AiProfilePrompt.DEFAULT
                    : new AiProfilePrompt(AiProfilePrompt.Style.fromId(input.readUTF()), input.readUTF());
            AiConfig config = new AiConfig(endpoint, apiKey, model, prompt);
            if (input.available() != 0) {
                throw new IOException("Unexpected configuration data");
            }
            return config;
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private static byte[] serialize(AiConfig config) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(config.getEndpoint());
            output.writeUTF(config.getApiKey());
            output.writeUTF(config.getModel());
            output.writeUTF(config.getProfilePrompt().getStyle().getId());
            output.writeUTF(config.getProfilePrompt().getCustomText());
        }
        return bytes.toByteArray();
    }
}
