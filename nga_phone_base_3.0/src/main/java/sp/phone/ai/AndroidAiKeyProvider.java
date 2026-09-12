package sp.phone.ai;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

final class AndroidAiKeyProvider implements AiConfigStore.KeyProvider {
    private static final String PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "sp.phone.ai.config.v1";
    private final String alias;

    AndroidAiKeyProvider() {
        this(KEY_ALIAS);
    }

    AndroidAiKeyProvider(String alias) {
        this.alias = alias;
    }

    @Override
    public SecretKey getExisting() throws GeneralSecurityException, IOException {
        Key key = openKeyStore().getKey(alias, null);
        if (key == null) {
            return null;
        }
        if (!(key instanceof SecretKey)) {
            throw new GeneralSecurityException("Invalid AI configuration key");
        }
        return (SecretKey) key;
    }

    @Override
    public SecretKey create() throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
        generator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    @Override
    public void delete() throws GeneralSecurityException, IOException {
        openKeyStore().deleteEntry(alias);
    }

    private static KeyStore openKeyStore() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(PROVIDER);
        keyStore.load(null);
        return keyStore;
    }
}
