# Authorized Commit and Delivery Plan

The user explicitly requested commit, finish-work, and push after validation.
Validation and independent review are complete; no further approval is needed.

## 1. Work Commit

Message: `fix(android): simplify floor menus and repair thread caching`

Files:

- `nga_phone_base_3.0/src/main/res/menu/article_list_context_menu.xml`
- `nga_phone_base_3.0/src/main/res/menu/article_list_context_menu_with_tid.xml`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java`
- `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleCacheActivity.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticlePageCache.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/presenter/ArticlePageCacheTest.java`
- `.trellis/spec/frontend/component-guidelines.md`
- `.trellis/spec/frontend/index.md`
- `.trellis/spec/backend/thread-page-cache-contract.md`
- `.trellis/spec/backend/index.md`

## 2. Task Archive Commit

Use `task.py archive 09-11-thread-menu-cache-fixes` to commit only this task's
artifacts at their archive destination. The script uses narrow task paths.

## 3. Journal Commit and Push

Use `add_session.py` to record the implementation, review correction, validation
results/limits, and the work-commit hash. Push the resulting
`fix/thread-menu-cache` branch to `origin` without rewriting history.

## Unrelated Dirty Path

`.trellis/tasks/09-11-upstream-august-2026-review/` existed before this session.
It is excluded from all commits and remains untouched.
