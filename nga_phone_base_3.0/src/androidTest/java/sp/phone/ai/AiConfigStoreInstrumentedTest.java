package sp.phone.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.util.AtomicFile;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/** Compile by default; execute only after explicit authorization for device tests. */
@RunWith(AndroidJUnit4.class)
public class AiConfigStoreInstrumentedTest {
    private File file;
    private AndroidAiKeyProvider keys;
    private AiConfigStore store;

    @Before
    public void createIsolatedStorage() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix = UUID.randomUUID().toString();
        file = new File(context.getNoBackupFilesDir(), "ai-config-test-" + suffix + ".bin");
        keys = new AndroidAiKeyProvider("sp.phone.ai.test." + suffix);
        store = new AiConfigStore(new AndroidAiConfigFile(file), keys);
    }

    @After
    public void removeIsolatedStorage() throws Exception {
        store.clear();
    }

    @Test
    public void keystoreCiphertextReopensAndClearDeletesBothSides() throws Exception {
        AiConfig config = new AiConfig("https://example.test/v1", "synthetic-device-test-key", "synthetic-model");
        store.save(config);
        assertNull(keys.getExisting().getEncoded());
        String stored = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
        assertFalse(stored.contains(config.getApiKey()));
        AiConfig reopened = new AiConfigStore(new AndroidAiConfigFile(file), keys).load();
        assertEquals(config.getEndpoint(), reopened.getEndpoint());
        assertEquals(config.getApiKey(), reopened.getApiKey());
        store.clear();
        assertFalse(file.exists());
        assertNull(keys.getExisting());
        assertNull(store.load());
    }

    @Test
    public void interruptedAtomicReplacementPreservesCommittedConfiguration() throws Exception {
        store.save(new AiConfig("https://example.test/v1", "synthetic-original-key", "synthetic-model"));
        // Model process death: close a partial transaction without either commit or rollback.
        try (FileOutputStream partial = new AtomicFile(file).startWrite()) {
            partial.write(new byte[]{1, 2, 3});
            partial.getFD().sync();
        }
        AiConfig reopened = new AiConfigStore(new AndroidAiConfigFile(file), keys).load();
        assertEquals("synthetic-original-key", reopened.getApiKey());
    }

    @Test
    public void lostKeystoreKeyCannotRestoreOrReuseCiphertext() throws Exception {
        store.save(new AiConfig("https://example.test/v1", "synthetic-test-key", "synthetic-model"));
        keys.delete();
        assertThrows(AiConfigStore.StorageException.class, store::load);
        assertFalse(file.exists());
        assertNull(keys.getExisting());
        assertNull(store.load());
    }
}
