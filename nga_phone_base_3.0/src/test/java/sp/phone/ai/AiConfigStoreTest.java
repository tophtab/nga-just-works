package sp.phone.ai;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import sp.phone.ai.summary.ProfileSummaryInput;
import sp.phone.ai.summary.ProfileSummaryLoader;
import sp.phone.ai.summary.SummaryController;

public class AiConfigStoreTest {
    private final MemoryFile file = new MemoryFile();
    private final MemoryKeys keys = new MemoryKeys();
    private final AiConfigStore store = new AiConfigStore(file, keys);

    @Test
    public void absentConfigurationDoesNotCreateAKey() throws Exception {
        assertNull(store.load());
        assertEquals(0, keys.created);
    }

    @Test
    public void savedConfigReopensAndReplacementIsOneRecord() throws Exception {
        store.save(config("first"));
        AiConfig reopened = new AiConfigStore(file, keys).load();
        assertEquals("first-key", reopened.getApiKey());
        store.save(config("second"));
        AiConfig second = store.load();
        assertEquals("https://second.example.test/v1/chat/completions", second.getEndpoint());
        assertEquals("second-key", second.getApiKey());
        assertEquals("second-model", second.getModel());
        assertEquals(1, keys.created);
    }

    @Test
    public void legacyStoredConfigLoadsWithoutCreatingAKeyAndUpgradesOnTheNextSave() throws Exception {
        keys.key = AiConfigRecordTest.newKey();
        file.bytes = AiConfigRecordTest.legacyRecord(config("first"), keys.key);
        byte[] legacy = file.bytes.clone();
        AiConfig loaded = store.load();
        assertEquals(AiProfilePrompt.DEFAULT, loaded.getProfilePrompt());
        assertEquals("first-key", loaded.getApiKey());
        assertArrayEquals(legacy, file.bytes);
        assertEquals(0, keys.created);
        store.save(loaded);
        assertEquals(2, file.bytes[4]);
        assertEquals(loaded.getProfilePrompt(), new AiConfigStore(file, keys).load().getProfilePrompt());
        assertEquals(0, keys.created);
    }

    @Test
    public void retainedCustomTextAndSelectionSurviveReopeningForEveryStyle() throws Exception {
        String text = "  Custom prompt\n第二段 😀\r\n";
        for (AiProfilePrompt.Style style : AiProfilePrompt.Style.values()) {
            AiProfilePrompt prompt = new AiProfilePrompt(style, text);
            store.save(config("first", prompt));
            AiConfig reopened = new AiConfigStore(file, keys).load();
            assertEquals(prompt, reopened.getProfilePrompt());
            assertEquals(text, reopened.getProfilePrompt().getCustomText());
        }
    }

    @Test
    public void reopenedCustomPromptFlowsThroughCollectionToTheModelUsingTheControllerSnapshot() throws Exception {
        String customText = "CUSTOM_ROUTE_SENTINEL\n  用两句话说明样本支持的观点。\n";
        AiProfilePrompt prompt = new AiProfilePrompt(AiProfilePrompt.Style.CUSTOM, customText);
        store.save(config("first", prompt));
        List<String> sentPrompts = new ArrayList<>();
        List<AiConfig> sentConfigs = new ArrayList<>();
        int[] reads = {0};
        ProfileSummaryLoader loader = new ProfileSummaryLoader((uid, kind, callback) -> {
            callback.onSuccess(new ProfileSummaryLoader.Page(uid, kind, Collections.singletonList(
                    new ProfileSummaryInput.Entry("Public topic", "Board", "2026-01-01", "Public reply"))));
            return SummaryController.Cancelable.NONE;
        });
        SummaryController controller = new SummaryController(() -> {
            reads[0]++;
            return new AiConfigStore(file, keys).load();
        }, (config, input, callback) -> {
            sentConfigs.add(config);
            sentPrompts.add(input);
            callback.onSuccess("Synthetic result");
            return SummaryController.Cancelable.NONE;
        }, Runnable::run, () -> "profile:42", new SummaryController.Listener() {
            @Override
            public void onState(SummaryController.State state) { }

            @Override
            public void onConfigurationRequired() {
                throw new AssertionError("Stored configuration is present");
            }
        });
        controller.start("profile:42", (config, callback) ->
                loader.load("42", "Viewed user", config.getProfilePrompt(), callback));
        assertEquals(2, reads[0]);
        assertEquals(1, sentPrompts.size());
        assertEquals(prompt, sentConfigs.get(0).getProfilePrompt());
        assertTrue(sentPrompts.get(0).contains("输出要求：\n" + customText + "\n"));
        assertTrue(sentPrompts.get(0).contains("[回复1] Public topic"));
        assertTrue(sentPrompts.get(0).contains("回复正文：Public reply"));
        assertFalse(sentPrompts.get(0).contains(AiProfilePrompt.DEFAULT.getInstructions()));
        assertEquals(SummaryController.Status.SUCCESS, controller.getState().getStatus());
    }

    @Test
    public void clearRemovesRecordAndKeyAndCanBeRepeated() throws Exception {
        store.save(config("first"));
        store.clear();
        store.clear();
        assertNull(file.bytes);
        assertNull(keys.key);
        assertNull(store.load());
    }

    @Test
    public void losingKeyNeverGeneratesAReplacementWhileLoading() throws Exception {
        store.save(config("first"));
        keys.key = null;
        AiConfigStore.StorageException error = assertThrows(AiConfigStore.StorageException.class, store::load);
        assertEquals("AI 配置已失效，请重新配置", error.getMessage());
        assertNull(error.getCause());
        assertEquals(1, keys.created);
        assertNull(file.bytes);
        assertNull(store.load());
    }

    @Test
    public void tamperedAndWrongKeyRecordsAreDiscarded() throws Exception {
        store.save(config("first"));
        file.bytes[file.bytes.length - 1] ^= 1;
        assertThrows(AiConfigStore.StorageException.class, store::load);
        assertNull(file.bytes);
        assertNull(keys.key);
        store.save(config("second"));
        keys.key = AiConfigRecordTest.newKey();
        assertThrows(AiConfigStore.StorageException.class, store::load);
        assertNull(file.bytes);
        assertNull(keys.key);
        assertNull(store.load());
    }

    @Test
    public void failedAtomicSavePreservesThePreviousConfiguration() throws Exception {
        AiProfilePrompt originalPrompt = new AiProfilePrompt(AiProfilePrompt.Style.CUSTOM, "Original\nPrompt");
        store.save(config("first", originalPrompt));
        byte[] original = file.bytes.clone();
        file.failWrites = true;
        AiConfigStore.StorageException error = assertThrows(AiConfigStore.StorageException.class,
                () -> store.save(config("second", new AiProfilePrompt(AiProfilePrompt.Style.DETAILED, ""))));
        assertArrayEquals(original, file.bytes);
        assertEquals("first-key", store.load().getApiKey());
        assertEquals(originalPrompt, store.load().getProfilePrompt());
        assertFalse(error.toString().contains("synthetic-private-value"));
        assertNull(error.getCause());
    }

    @Test
    public void temporarilyUnavailableKeystoreDoesNotDestroyTheRecord() throws Exception {
        store.save(config("first"));
        byte[] original = file.bytes.clone();
        keys.failReads = true;
        AiConfigStore.StorageException error = assertThrows(AiConfigStore.StorageException.class, store::load);
        assertArrayEquals(original, file.bytes);
        assertFalse(error.toString().contains("synthetic-private-value"));
        assertNull(error.getCause());
        keys.failReads = false;
        assertEquals("first-key", store.load().getApiKey());
    }

    @Test
    public void failedFileDeletionStillDestroysTheKey() throws Exception {
        store.save(config("first"));
        file.failDeletes = true;
        assertThrows(AiConfigStore.StorageException.class, store::clear);
        assertNull(keys.key);
        assertThrows(AiConfigStore.StorageException.class, store::load);
        file.failDeletes = false;
        store.clear();
        assertNull(store.load());
    }

    @Test
    public void failedKeyDeletionStillRemovesConfiguration() throws Exception {
        store.save(config("first"));
        keys.failDeletes = true;
        assertThrows(AiConfigStore.StorageException.class, store::clear);
        assertNull(file.bytes);
        assertNull(store.load());
        keys.failDeletes = false;
        store.clear();
        assertNull(keys.key);
    }

    private static AiConfig config(String name) {
        return new AiConfig("https://" + name + ".example.test/v1", name + "-key", name + "-model");
    }

    private static AiConfig config(String name, AiProfilePrompt prompt) {
        return new AiConfig("https://" + name + ".example.test/v1", name + "-key", name + "-model", prompt);
    }

    private static final class MemoryFile implements AiConfigStore.RecordFile {
        byte[] bytes;
        boolean failWrites;
        boolean failDeletes;

        @Override
        public byte[] read() {
            return bytes == null ? null : bytes.clone();
        }

        @Override
        public void write(byte[] ciphertext) throws IOException {
            if (failWrites) {
                throw new IOException("synthetic-private-value");
            }
            bytes = ciphertext.clone();
        }

        @Override
        public void delete() throws IOException {
            if (failDeletes) {
                throw new IOException("synthetic-private-value");
            }
            bytes = null;
        }
    }

    private static final class MemoryKeys implements AiConfigStore.KeyProvider {
        SecretKey key;
        int created;
        boolean failReads;
        boolean failDeletes;

        @Override
        public SecretKey getExisting() throws GeneralSecurityException {
            if (failReads) {
                throw new GeneralSecurityException("synthetic-private-value");
            }
            return key;
        }

        @Override
        public SecretKey create() throws GeneralSecurityException {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            key = generator.generateKey();
            created++;
            return key;
        }

        @Override
        public void delete() throws GeneralSecurityException {
            if (failDeletes) {
                throw new GeneralSecurityException("synthetic-private-value");
            }
            key = null;
        }
    }
}
