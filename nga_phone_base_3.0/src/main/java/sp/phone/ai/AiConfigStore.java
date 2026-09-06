package sp.phone.ai;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.crypto.SecretKey;

/** Owns the only saved AI configuration; no preferences, public files, or backup data are used. */
public final class AiConfigStore {
    // Settings and summaries may create separate stores. Serialize their file/key transactions.
    private static final Object STORAGE_LOCK = new Object();
    private final RecordFile file;
    private final KeyProvider keys;

    public AiConfigStore(Context context) {
        this(new AndroidAiConfigFile(context.getApplicationContext()), new AndroidAiKeyProvider());
    }

    AiConfigStore(RecordFile file, KeyProvider keys) {
        this.file = file;
        this.keys = keys;
    }

    @Nullable
    public AiConfig load() throws StorageException {
        synchronized (STORAGE_LOCK) {
            final byte[] record;
            final SecretKey key;
            try {
                record = file.read();
                if (record == null) {
                    return null;
                }
                key = keys.getExisting();
            } catch (IOException | GeneralSecurityException | RuntimeException ignored) {
                throw new StorageException("无法读取安全 AI 配置，请稍后重试");
            }
            if (key != null) {
                try {
                    return AiConfigRecord.decrypt(record, key);
                } catch (IOException | GeneralSecurityException | RuntimeException ignored) {
                    // A corrupt or invalidated record never falls back to a plaintext or cached key.
                }
            }
            discardInvalidRecord();
            throw new StorageException("AI 配置已失效，请重新配置");
        }
    }

    public void save(AiConfig config) throws StorageException {
        if (config == null) {
            throw new StorageException("AI 配置不完整，请重新填写");
        }
        synchronized (STORAGE_LOCK) {
            try {
                SecretKey key = keys.getExisting();
                if (key == null) {
                    key = keys.create();
                }
                // The entire config is one atomic ciphertext, so endpoint/model/key cannot mix.
                file.write(AiConfigRecord.encrypt(config, key));
            } catch (IOException | GeneralSecurityException | RuntimeException ignored) {
                throw new StorageException("无法安全保存 AI 配置，请稍后重试");
            }
        }
    }

    public void clear() throws StorageException {
        synchronized (STORAGE_LOCK) {
            boolean failed = false;
            try {
                file.delete();
            } catch (IOException | RuntimeException ignored) {
                failed = true;
            }
            // Still delete the key if deleting the ciphertext failed, making remnants unreadable.
            try {
                keys.delete();
            } catch (IOException | GeneralSecurityException | RuntimeException ignored) {
                failed = true;
            }
            if (failed) {
                throw new StorageException("未能完整清除 AI 配置，请重试");
            }
        }
    }

    private void discardInvalidRecord() {
        try {
            file.delete();
        } catch (IOException | RuntimeException ignored) {
            // An undeletable invalid record is still rejected on every load.
        }
        try {
            keys.delete();
        } catch (IOException | GeneralSecurityException | RuntimeException ignored) {
            // No record/key is returned to callers on this path.
        }
    }

    interface RecordFile {
        @Nullable byte[] read() throws IOException;
        void write(byte[] ciphertext) throws IOException;
        void delete() throws IOException;
    }

    interface KeyProvider {
        @Nullable SecretKey getExisting() throws GeneralSecurityException, IOException;
        SecretKey create() throws GeneralSecurityException, IOException;
        void delete() throws GeneralSecurityException, IOException;
    }

    public static final class StorageException extends Exception {
        private StorageException(String safeMessage) {
            // Deliberately no cause: provider/IO exception text may contain private values.
            super(safeMessage);
        }
    }
}
