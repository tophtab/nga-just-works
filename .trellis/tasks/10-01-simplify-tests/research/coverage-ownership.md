# Coverage ownership index

The third-pass baseline is 618 JVM methods in 67 files. Audit ownership uses
exclusive paths: AI/profile plus AI fragment editor tests (328), all other JVM
tests (290), Python (37), and actual Android storage instrumentation (3).
This differs from the PRD's feature-domain grouping: the 21 AI fragment tests
were originally counted in UI, and are assigned to the AI implementation owner.
No tests under external `references/` checkouts belong to this project's gate.

- [AI/profile owners and complete per-suite counts](ai-profile-ownership.md)
- [AI/profile method and matrix deletion ledger](ai-profile-deletions.md)
- [Reader/UI/common owners, including every retained method](reader-ui-ownership.md)
- [Reader/UI/common deletion ledger](reader-ui-deletions.md)
- [Python/device complete necessity audit](python-device-ownership.md)
- [Python method and input-row deletion ledger](python-device-deletions.md)
- [Independent review and restored COMMENT integration regression](comprehensive-review.md)

Final ownership totals: AI/profile/editor 250; other JVM 222; Python 32; device 3.
Unique account/session, secret/export, supported migration/corruption, cancelled
work and destructive release boundaries retain explicit owners in these audits.
Higher layers retain caller-specific integration risks: the COMMENT raw-floor
regression was restored because lower-layer classification alone cannot catch
the prompt builder bypassing that classifier.
