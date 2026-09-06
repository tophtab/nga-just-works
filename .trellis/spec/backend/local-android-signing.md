# Local Android Signing and Recovery

## 1. Scope / Trigger

Use this contract when the maintainer requests a local signed APK, an update
that preserves the installed application's data, or signing-key recovery on
a replacement computer. The authorization and Windows ADB rules in
[Android Quality and Instrumentation](./android-quality-guidelines.md) still
apply; discovering a key does not authorize building or operating a device.

## 2. Signatures

The maintainer's WSL signing backup was verified on 2026-09-06:

```text
Directory:   /home/toph/.config/nga-just-works/signing/
Keystore:    nga-just-works-release.p12
Credentials: credentials.env
Format:      PKCS#12, private-key entry
File modes:  0600 for both files
```

`credentials.env` defines the four existing Gradle inputs:

```text
ANDROID_SIGNING_STORE_FILE
ANDROID_SIGNING_STORE_PASSWORD
ANDROID_SIGNING_KEY_ALIAS
ANDROID_SIGNING_KEY_PASSWORD
```

The public signing-certificate SHA-256 fingerprint is:

```text
e944475ac92ee7ab99c1da790dc1bbda4332db1c3c332033f32693cc9b53993c
```

Compare fingerprints after removing separators and normalizing letter case.
This certificate fingerprint is public verification metadata, not a private key.

## 3. Contracts

- Check this known directory and its `.p12` file before concluding that local
  signing is unavailable. An unset process environment or a search limited to
  `*.jks` / `*.keystore` is insufficient evidence.
- Load the credentials privately into the build process environment. Do not
  print their values, enable shell tracing, put passwords into literal command
  arguments, or copy signing material into a worktree.
- A data-preserving update uses the production applicationId
  `com.github.tophtab.ngajustworks` and the original signing certificate. The
  ordinary `debug` variant has a `.debug` suffix and a different signing key.
  Use the existing `preview` variant for an explicitly requested signed,
  debuggable local APK.
- Set `CI_VERSION_NAME` and `CI_VERSION_CODE` together. Derive the code with
  `scripts/derive_android_version_code.py` and choose an upgrade above the
  installed version. The unconfigured local fallback `4.5.0` / `4050` is not an
  upgrade for current releases. A local-only `X.Y.Z-debug.N` label may use the
  derived build slot for `N`; published builds retain the workflow's run number.
- GitHub Actions already holds the same signing material in
  `ANDROID_SIGNING_KEYSTORE_BASE64`, `ANDROID_SIGNING_STORE_PASSWORD`,
  `ANDROID_SIGNING_KEY_ALIAS`, and `ANDROID_SIGNING_KEY_PASSWORD`. The normal
  Secrets UI/API exposes metadata rather than the original values. CI can
  still sign if the local copy is lost; Secrets are not a convenient sole
  recovery backup. Base64 encoding is not encryption.
- Back up the original `.p12` and all required credentials in an encrypted
  password-manager attachment or encrypted archive, plus an independent
  encrypted copy outside this computer. Keep the vault/archive recovery
  material available independently of this computer as well.
- Do not commit the keystore, `credentials.env`, or a base64 copy to this
  public repository, Releases, or ordinary Actions artifacts. If the maintainer
  chooses GitHub as an additional backup location, use a separately encrypted
  archive in a private repository and keep its decryption key/password outside
  GitHub. Private repository visibility alone is not the encryption boundary.
- On a replacement computer, restore both files outside the repository,
  restrict access, update the absolute `ANDROID_SIGNING_STORE_FILE` path in
  the restored configuration, and verify the certificate before building.
  Do not generate a replacement key with the same filename or alias: it would
  have a different identity and cannot perform the existing sideloaded app's
  normal in-place updates.
- A suggested backup destination is not a completed backup. Record separately
  whether an off-device copy was actually created and restoration was tested.

## 4. Validation & Error Matrix

| Condition | Required behavior |
| --- | --- |
| Signing variables are unset | Inspect the known local credential file before declaring the key missing |
| Restored configuration points at the old computer's path | Correct only the local store path; preserve the original key |
| Store/key password is invalid or the private-key entry is absent | Stop signing and report the failing stage without disclosing credentials |
| Certificate differs from the recorded or installed signer | Stop the update; do not uninstall or clear the installed app to work around it |
| Built APK has a `.debug` suffix or would downgrade the app | Correct the variant/version before installation |
| Local copy is missing but GitHub Secrets remain | Existing CI signing remains available; arrange an explicitly authorized secure recovery if needed |
| Neither the original private key nor a recoverable copy remains | A new unrelated key cannot provide the existing sideloaded app's normal in-place update |

## 5. Good / Base / Bad Cases

- **Good:** a restored encrypted backup opens successfully, its certificate
  matches the recorded fingerprint, and an APK signed with it passes verification.
- **Base:** the local key is verified and CI signing exists, but no independent
  backup has been created or restored. State that limitation explicitly.
- **Bad:** assume the key is unavailable after checking environment variables,
  regenerate it during migration, or treat a public/base64 upload as a backup.

## 6. Tests Required

- When verifying or restoring signing material, validate the store password
  and private-key entry with `keytool`, then compare the public certificate.
- For an explicitly requested build, `assemblePreview` must successfully use
  the configured private-key password. Verify the resulting APK with
  `apksigner verify --print-certs` and inspect its applicationId, version,
  minSdk/targetSdk, and debuggable flag before installation.
- Only after explicit device authorization, compare the actual installed APK's
  certificate, install with the exact serial and Windows ADB using
  `install --no-streaming -r -t`, and verify the installed version. The app ID,
  data directory, and first-install time must remain unchanged for an update.
- A real backup check restores from the independent encrypted copy; successful
  use of the original local file alone is not a backup-restoration test.
- Documentation-only changes need link/content/whitespace checks, not a new
  APK build, device operation, or Trellis runtime test.

## 7. Wrong vs Correct

### Wrong

```bash
# Neither committing the original key nor base64-encoding it makes a safe backup.
git add credentials.env nga-just-works-release.p12
```

### Correct

Read the verified, owner-controlled configuration outside the repository and
print only public certificate metadata:

```bash
set -euo pipefail
set +x
set -a
. "$HOME/.config/nga-just-works/signing/credentials.env"
set +a
test -f "$ANDROID_SIGNING_STORE_FILE"
keytool -J-Duser.language=en -J-Duser.country=US \
  -list -v -storetype PKCS12 \
  -keystore "$ANDROID_SIGNING_STORE_FILE" \
  -storepass:env ANDROID_SIGNING_STORE_PASSWORD \
  -alias "$ANDROID_SIGNING_KEY_ALIAS" |
  sed -n 's/^[[:space:]]*SHA256: /SHA256: /p'
```

References: [Android app signing](https://developer.android.com/studio/publish/app-signing)
and [GitHub repository-secret metadata](https://docs.github.com/en/rest/actions/secrets#get-a-repository-secret).
