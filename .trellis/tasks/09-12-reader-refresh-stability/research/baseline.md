# Baseline and regression provenance

## Release 5.6.1

Verified tag `5.6.1` resolves to `9e0b59d2`, committed 2026-09-05 10:52 +0800.
Its `ArticleListAdapter.setData` (line 382) only assigns the response. A retained
array holds body WebViews, and body binding selects the existing view for a
position. `LocalWebView.loadDataWithBaseURL` (line 107) returns early for equal
HTML. `LocalWebView.java` and the two article-list layout XML files have no diff
between this tag and the initial task HEAD `dba4b289`.

The 5.6.1 adapter lacks the later author-location feature. The goal is to restore
the same stable refresh behavior while keeping that feature, not revert it.

## Introduced body regression

`7acc4e23` (2026-09-12 02:30 +0800), the task
`09-11-thread-detail-compat-mode`, changed the retained array to the real row
count but also called `releaseWebViews()` whenever `mData != data`. That routine
removes all existing WebViews from parents, stops them, and destroys them.
Fresh network responses normally have new object identities. Destroying every
view discards its previous HTML and bypasses the equality optimization even
when the server returns identical content.

The upstream snapshot `22ba3082` also retains views and skips equal HTML.
The equality guard was introduced by upstream `bd9fd9f9` on 2020-07-12 and is
still present locally. The previous root-cause narrative must be corrected:
a call to the override during row binding is not proof that the platform load
method actually executes.

`16cc185b` avoids redundant list binding on unchanged READY page entry. It
does not address fresh-response destruction during manual refresh. Keep its
view-lifetime guard and explicit blacklist-row update.

## Introduced metadata blank

`ef77d0c9` added `mAuthorLocations = Snapshot.empty()` to adapter data assignment
and `AuthorLocationService.Page`'s pre-subscription empty delivery. These create
a transient loss of already known locations even when the repository can
immediately return a fresh cache entry. The repository's metadata callbacks use
detail-only payloads, so they are not by themselves a body reload path.

This is source/history evidence, not a device recording or measured comparison.
