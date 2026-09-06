package sp.phone.ai;

import android.content.Context;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Android adapter: both committed and temporary records stay in the backup-excluded directory. */
final class AndroidAiConfigFile implements AiConfigStore.RecordFile {
    static final String FILE_NAME = "ai-config.bin";
    private final AtomicFile atomicFile;

    AndroidAiConfigFile(Context context) {
        this(new File(context.getNoBackupFilesDir(), FILE_NAME));
    }

    AndroidAiConfigFile(File file) {
        atomicFile = new AtomicFile(file);
    }

    @Override
    public byte[] read() throws IOException {
        try (FileInputStream input = atomicFile.openRead()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            // Return at most limit + 1; the codec rejects an oversized record without parsing it.
            while (bytes.size() <= AiConfigRecord.MAX_RECORD_BYTES) {
                int read = input.read(buffer, 0, Math.min(buffer.length,
                        AiConfigRecord.MAX_RECORD_BYTES + 1 - bytes.size()));
                if (read == -1) {
                    break;
                }
                bytes.write(buffer, 0, read);
            }
            return bytes.toByteArray();
        } catch (FileNotFoundException missing) {
            File base = atomicFile.getBaseFile();
            if (!base.exists() && !new File(base.getPath() + ".bak").exists()) {
                return null;
            }
            throw missing;
        }
    }

    @Override
    public void write(byte[] ciphertext) throws IOException {
        FileOutputStream output = null;
        try {
            output = atomicFile.startWrite();
            output.write(ciphertext);
            output.getFD().sync();
            atomicFile.finishWrite(output);
            output = null;
        } catch (IOException | RuntimeException failure) {
            atomicFile.failWrite(output);
            throw failure;
        }
        // AtomicFile.finishWrite reports some rename failures only through the platform logger.
        if (!Arrays.equals(ciphertext, read())) {
            throw new IOException("Configuration write did not commit");
        }
    }

    @Override
    public void delete() throws IOException {
        atomicFile.delete();
        File base = atomicFile.getBaseFile();
        if (base.exists() || new File(base.getPath() + ".bak").exists()
                || new File(base.getPath() + ".new").exists()) {
            throw new IOException("Configuration record could not be deleted");
        }
    }
}
