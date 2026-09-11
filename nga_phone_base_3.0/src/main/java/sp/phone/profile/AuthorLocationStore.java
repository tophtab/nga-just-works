package sp.phone.profile;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Versioned, bounded app-private file. Call only on the service's single disk executor. */
final class AuthorLocationStore {

    private static final int VERSION = 1;
    private static final int MAX_BYTES = 1024 * 1024;
    private final File file;

    AuthorLocationStore(File file) {
        this.file = file;
    }

    List<AuthorLocationCache.Entry> read() {
        if (!file.isFile() || file.length() > MAX_BYTES) {
            return Collections.emptyList();
        }
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (output.size() + count > MAX_BYTES) {
                    return Collections.emptyList();
                }
                output.write(buffer, 0, count);
            }
            return decode(new String(output.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    void write(List<AuthorLocationCache.Entry> entries) {
        File temporary = new File(file.getPath() + ".tmp");
        try {
            byte[] bytes = encode(entries).getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) {
                return;
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return;
            }
            try (FileOutputStream stream = new FileOutputStream(temporary)) {
                stream.write(bytes);
                stream.getFD().sync();
            }
            try {
                Files.move(temporary.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException ignored) {
            // Cache loss only causes a later miss. Do not log cached profile/account material.
        } finally {
            if (temporary.exists()) {
                //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
        }
    }

    private static String encode(List<AuthorLocationCache.Entry> entries) {
        JSONObject root = new JSONObject(true);
        root.put("version", VERSION);
        JSONArray rows = new JSONArray();
        int first = Math.max(0, entries.size() - AuthorLocationCache.MAX_ENTRIES);
        for (int i = first; i < entries.size(); i++) {
            AuthorLocationCache.Entry entry = entries.get(i);
            JSONObject row = new JSONObject(true);
            row.put("origin", entry.key.origin);
            row.put("account", entry.key.account);
            row.put("author", entry.key.author);
            row.put("kind", entry.kind.name());
            row.put("location", entry.location);
            row.put("observed", entry.observedAt);
            row.put("expires", entry.expiresAt);
            rows.add(row);
        }
        root.put("entries", rows);
        return JSON.toJSONString(root);
    }

    private static List<AuthorLocationCache.Entry> decode(String source) {
        JSONObject root = JSON.parseObject(source);
        if (root == null || root.getIntValue("version") != VERSION
                || !(root.get("entries") instanceof JSONArray)) {
            return Collections.emptyList();
        }
        JSONArray rows = root.getJSONArray("entries");
        if (rows.size() > AuthorLocationCache.MAX_ENTRIES) {
            return Collections.emptyList();
        }
        List<AuthorLocationCache.Entry> result = new ArrayList<>();
        for (Object item : rows) {
            if (!(item instanceof JSONObject)) {
                return Collections.emptyList();
            }
            JSONObject row = (JSONObject) item;
            String origin = row.getString("origin");
            String account = row.getString("account");
            int author = row.getIntValue("author");
            long observed = row.getLongValue("observed");
            long expires = row.getLongValue("expires");
            AuthorLocationCache.Kind kind = AuthorLocationCache.Kind.valueOf(row.getString("kind"));
            Object location = row.get("location");
            if (origin == null || !origin.equals(ProfileSession.normalizeOrigin(origin))
                    || !ProfileSession.isUid(account, true) || observed < 0 || expires <= observed
                    || !(location == null || location instanceof String)
                    || (location != null && !ProfileLocationParser.isDisplayableLocation((String) location))
                    || (kind == AuthorLocationCache.Kind.RATE_LIMIT ? author != 0 : author <= 0)
                    || (kind != AuthorLocationCache.Kind.OBSERVATION && location != null)
                    || (kind == AuthorLocationCache.Kind.OBSERVATION
                    && expires != AuthorLocationCache.addTime(observed, AuthorLocationCache.FRESH_MILLIS))
                    || (kind == AuthorLocationCache.Kind.FAILURE
                    && expires != AuthorLocationCache.addTime(observed, AuthorLocationCache.FAILURE_MILLIS))
                    || (kind == AuthorLocationCache.Kind.RATE_LIMIT
                    && expires < AuthorLocationCache.addTime(observed, AuthorLocationCache.RATE_LIMIT_MILLIS))) {
                return Collections.emptyList();
            }
            result.add(new AuthorLocationCache.Entry(
                    new AuthorLocationCache.Key(origin, account, author), kind,
                    (String) location, observed, expires));
        }
        return result;
    }
}
