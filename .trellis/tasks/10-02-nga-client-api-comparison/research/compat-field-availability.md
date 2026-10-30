# Compatibility endpoint field availability: live sample

2026-10-02 22:35:13 Asia/Shanghai. One explicitly requested authenticated read,
using .temp/cookies.txt, thread47659652/page1, unsigned POST to
https://ngabbs.com/app_api.php?__lib=post&__act=list. HTTP200/code0, matching
thread/page,19 rows. No raw response or credentials stored.

| Feature | Actual response evidence | Current mapping / conclusion |
| --- | --- | --- |
| Vote counts | All19 rows have integer vote_good and vote_bad | Not read by ThreadAppBean; scoreKnown=false. Confirmed available but unused |
| Attachments | Wire spelling attches;19 keys,2 non-null/nonempty lists;17 null. attachPrefix present.2 rows include inline-media markup | Dedicated list not mapped; inline content still rendered. Confirmed attachment lists available; no claim those images are necessarily invisible |
| Comment relationship | All19 rows have comment_to_id but every value is empty string; isTieTiao boolean present | Relationship field exists, populated semantics not demonstrated by this page; current adapter recognizes isTieTiao but not parent relation |
| Hot replies | Root hot_post exists and is null | Field exists; this sample does not demonstrate a populated hot-reply payload or whether this topic has eligible hot replies |

This corrects an ambiguous earlier summary: absence from current adapter is not
absence from endpoint. We have positive data evidence for counts and attachment
lists, schema-only evidence for comment parent/hot replies. Need a known-positive
comment/hot-reply sample before claiming complete support. Do not turn empty
values into evidence the endpoint can never provide those features.

Metadata artifact lnga-live-47659652-field-inventory.json contains key/type/count
summaries only. Its generic nonempty statistic is for structural presence, not
truthiness of boolean/zero numeric values. Attachment key attches is intentionally
preserved (it differs from attachs names used in other references).
