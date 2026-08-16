# Technical Design

## Boundaries

The change stays inside the existing Compose theme and Android resource layers. Compose screens will consume `MaterialTheme.colors` for semantic content/surface colors. Legacy editors will consume resource selectors so Android resolves light/night values without runtime theme branching.

## Implementation Shape

- `NavigationDrawerFragment.kt`: replace normal item `Color.DarkGray` label/icon colors with `MaterialTheme.colors.onBackground` (or the equivalent local content color). Keep header white content and the red count badge unchanged.
- `MessageDetailActivity.kt`: use `MaterialTheme.colors.onSurface` with reduced alpha for author/time metadata. Keep URL spans blue because they are an explicit link affordance.
- `SearchActivity.kt`: derive search field/chip surfaces and content colors from `MaterialTheme.colors`, passing explicit `BasicTextField` text style and using theme-aware placeholder, clear icon, divider, and chip colors. Avoid changing query behavior or layout.
- Add light/night color resources (and selectors where needed) for legacy editor surfaces, primary text, and hints; update `activity_change_avatar.xml` and `activity_change_sign_reply.xml` to reference them. Preserve the white editor surface in light mode and provide a dark surface/text pairing in night mode.

## Compatibility and Risk

- Material 2 is the active Compose theme boundary; no Material 3 components will be introduced.
- Existing explicit colors that represent links, status states, user-authored forum HTML, or toolbar content are not generalized.
- Resource changes are additive and can be independently reverted if a legacy editor has an unexpected theme overlay.

## Verification

- Add or extend source/resource contract assertions only where existing tests can verify stable theme contracts.
- Run the existing system/theme contract tests, relevant module JVM tests, and a debug compilation.
