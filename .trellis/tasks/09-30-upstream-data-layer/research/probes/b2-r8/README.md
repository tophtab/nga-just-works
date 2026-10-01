# B2 production-bean reflection shrink probe

Run from the dedicated worktree with JDK17 and the current app Debug classes:

```bash
./gradlew :nga_phone_base_3.0:u3FixtureClasspath -I .trellis/tasks/09-30-upstream-data-layer/research/probes/b2-r8/classpath.gradle --console=plain
python3 .trellis/tasks/09-30-upstream-data-layer/research/probes/b2-r8/run.py /tmp/u3-b2-test-classpath.txt /home/toph/.gradle/caches/modules-2/files-2.1/com.android.tools.build/builder/8.6.1/3ede92b2242a7a5b214479bd81c75c6c894308e8/builder-8.6.1.jar
```

The second argument is the **AGP-resolved** R8 code source, obtained from the
application plugin classloader `com.android.tools.r8.R8.protectionDomain.codeSource`.
For current AGP8.6.1 this is embedded in builder-8.6.1.jar, R8 8.6.27; its SHA256
is recorded in result.txt. Resolve again after a toolchain change; do not assume
an unrelated cached R8 is equivalent.

The script extracts unmodified production classfiles for TopicListBean and all
nested beans, ThreadPageInfo/ReplyInfo, BoardEntity (real Compose state delegate),
FilterKeyword, ReportTask.ResultBean, NonameUploadResponse, and JavaBean. All
other app/dependency classes are library inputs. Original chosen bean classes
are excluded from the library and post-shrink JVM classpath, so missing shrunk
beans cannot be masked by an unshrunk copy. The old JSON oracle is excluded.

R8 runs classfile release shrinking/optimization/obfuscation using the actual
app proguard.cfg and lib_base_common consumer-rules.pro. The only additional
keep is the generic harness main. No keep-all fixture bean rule, dontshrink,
dontoptimize or dontobfuscate is added. Names and method names enter at runtime
from cases.json, not constants recognizable during shrinking. Mapping shows
the harness helper obfuscated and marker-protected production names retained.

The post-shrink assertions decode nested data, test alias values and canonical
writer keys, call reflected getters, run FilterKeyword.match to populate the
compiled Pattern then verify its exclusion, and verify cache/icon exclusions
in both directions. The emitted Board children tree demonstrates generic
nested bean reconstruction. result.txt, mapping.txt and configuration.txt
are from the final run after removing the obsolete fastjson1 keep rule.
Unmatched-rule informational messages correspond to unrelated app rules whose
classes are library inputs rather than program inputs in this focused probe.

This verifies production bean reflection on a host JVM with real production
rules. It is **not** full-app DEX shrinking or minified Android execution. The
actual app minify dry run is blocked by the repository's signing task guard;
see ../../b2-r8-guard.log and ../../b2-evidence.md. U3-A7 remains pending for U4.
