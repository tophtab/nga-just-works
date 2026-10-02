# Approved Commit and Release Plan

User authorization: “完成后 commit、push、finish work，还有发布新版本”.

## Work Commit

`fix: align compatibility reader with default display`

- `.trellis/spec/backend/thread-detail-compat-contract.md`
- `lib_core/src/main/java/gov/anzong/androidnga/core/corebuild/HtmlCommentBuilder.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/AppArticleParser.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleRowPresentation.kt`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ThreadAppBean.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/thread/AppArticleParserTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/convert/AppArticleRenderingTest.kt`
- `release-notes/6.2.4.md`
- This task's requirements, design, implementation plan, context manifests,
  field evidence, verification record, metadata and commit plan.

## Bookkeeping and Publication

1. Archive this completed task and record the developer session, using the
   Trellis finish-work scripts and their separate bookkeeping commits.
2. Fast-forward local main from the tested feature branch; verify remote main
   still equals the inspected a7d7edb0 base and that 6.2.4 does not exist.
3. Create immutable 6.2.4 on the final main commit and push main plus that tag.
   Existing GitHub Actions builds/signs/publishes it. Do not poll that run.

## Excluded Existing Work

`.trellis/tasks/10-02-nga-client-api-comparison/` predates this task and remains
untouched and outside all commits. Ignored cookies, raw/local evidence, Gradle
outputs and temporary logs are not staged.
