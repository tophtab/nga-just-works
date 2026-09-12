# Investigation baseline

Date: 2026-09-12. Worktree: `/home/toph/nga-just-works-ai-summary`.
Branch and baseline: `feature/ai-summary@9a8113a9`.

## Reported symptom

The maintainer reports `NGA内容格式异常，请稍后重试`. The corresponding fixed
source message is `NGA 内容格式异常，请稍后重试` in `NgaProfilePageSource`.
The source forwards this error through the profile loader before the model
request is constructed. No live model call is needed to localize this symptom.

## Executed baseline test

```text
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'sp.phone.ai.*' --tests 'sp.phone.ai.summary.*' --console=plain
BUILD SUCCESSFUL in 28s
223 actionable tasks: 1 executed, 222 up-to-date
```

JUnit XML inspection: 204 tests in 13 AI classes, zero failures, errors, skips.
The test task itself executed; production compilation was up to date. These
are offline JVM tests and are not live NGA or device verification.

## Relevant earlier changes

- `1931bc35`: ignore explicitly unavailable activity records before checking
  available-record authors; unmarked mismatches still fail.
- `0dcc4d6e`: stream summaries and separate reasoning from the answer.
- `0482795c`: fetch original topic bodies using `read.php` and parse them with
  `NgaTopicBodyParser`; topic details are now additional collection boundaries.

Earlier topic-body validation explicitly used synthetic response fixtures and
did not claim live NGA compatibility. A passing baseline therefore does not
exclude an unsupported server response shape.
