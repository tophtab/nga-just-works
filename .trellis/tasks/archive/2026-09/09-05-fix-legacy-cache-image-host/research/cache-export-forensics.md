# Issue #6 cache export forensics (redacted)

Date: 2026-09-05 (Asia/Shanghai)

## Input

- Local-only artifact: `.temp/cache_20260905084551.zip`
- SHA-256: `ed60bcf9b12adf58cbf9b43312dba9b04255f9d9de8129c9a6d054a1348f93ce`
- Privacy rule: do not commit, quote, or copy post bodies, usernames, user IDs, or real media paths from this archive.

## Archive shape

- Valid ZIP, 12 KB compressed and 37,593 bytes uncompressed.
- Entries: two directories, one topic descriptor JSON, and two page JSON files.
- No JPG, PNG, GIF, WebP, video, audio, or other media binaries are present.
- Both page JSON files parse successfully and contain 20 rows.

## Redacted page facts

- Both pages contain `data.__GLOBAL._ATTACH_BASE_VIEW = img.nga.178.com/attachments`.
- The top-level page `time` values correspond to 2024-11-19.
- Post bodies contain 3 dot-relative `[img]./...[/img]` references in total.
- The pages contain 2 image attachment records with bare relative `attachurl` values.
- After deduplication across those two forms, 4 unique attachment paths remain.
- User objects contain 23 absolute `img.nga.178.com/avatars/...` avatar references.

## Limited diagnostic observation

- On 2026-09-05, `img.nga.178.com` did not resolve while `img.nga.cn` did.
- Each of the 4 unique attachment paths returned `200 image/jpeg` when resolved beneath
  `https://img.nga.cn/attachments`; the old-host requests produced no HTTP status because the host did not resolve.
- One redacted avatar path returned `200 image/jpeg` beneath `https://img.nga.cn/avatars`; the old-host request did not resolve.
- These observations establish the failure mechanism for the supplied artifact only and are not an availability guarantee. Implementation tests must be offline and fixture-based; do not repeat live probes without a new explicit authorization and stop plan.

## Code-path evidence

- `ArticleListModel.cachePage` writes the topic descriptor and raw response JSON only.
- `TopicListPresenter.exportCacheTopic` zips `files/cache/`; import unzips it into `filesDir`.
- `ArticleListModel.loadCachePage` reparses the saved raw JSON with `ArticleConvertFactory`.
- `ArticleConvertFactory.resolveAttachmentsPrefix` reads the historical `_ATTACH_BASE_VIEW`.
- `NgaImageHost.resolveAttachmentsPrefix` currently accepts any syntactically valid server attachment host in auto mode, including a retired legacy host.
- `ForumImageDecoder` expands dot-relative body images with that page prefix; `HtmlAttachmentBuilder` does the same for attachment records.
- `FunctionUtils.parseAvatarUrl` currently returns the extracted absolute avatar URL without legacy-host normalization, so Glide receives the retired host directly.

## Conclusion

The export/import process did not delete images. The archive never contained image bytes. On reload, the app reconstructs remote image URLs from relative paths plus a page-scoped host preserved in historical JSON; auto mode trusts the retired host, so network loading fails. The supplied attachment objects still exist at the same paths on the current attachment host at the time of the diagnostic.
