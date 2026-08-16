# Complete dark mode text color adaptation

## Goal

Remove remaining user-visible dark-mode contrast failures in the existing Android UI without changing intentional toolbar/icon styling or the established gray helper-text hierarchy.

## Confirmed Background

- The previous dark-mode task covered system navigation bars, the filter screen's Material 2 component boundary, and four size sliders. It was not a repository-wide color audit.
- `NavigationDrawerFragment` renders its item labels/icons in `Color.DarkGray` over `MaterialTheme.colors.background`, which is `#080C10` in the Compose dark palette (`NavigationDrawerFragment.kt:253,279,308`).
- `MessageDetailActivity` hard-codes author/time metadata to `Color(0xFF294563)` (`MessageDetailActivity.kt:141,147`), a low-contrast dark blue on the dark Compose surface.
- `SearchActivity` hard-codes white surfaces and gray placeholder/history content (`SearchActivity.kt:103,117,130,207-231`), bypassing the active Compose palette.
- Legacy avatar/signature editors hard-code `@color/white` backgrounds and `@color/black` text (`activity_change_avatar.xml:22,33,37,48`; `activity_change_sign_reply.xml:36,44`).
- White toolbar content, the filter screen's two gray helper texts, and explicitly colored forum/user content are intentional visual hierarchies and are not part of this correction.

## Requirements

1. The navigation drawer's normal labels and icons must use theme content colors and remain readable in both light and dark modes; the primary header's white content remains unchanged.
2. Message detail author and timestamp metadata must use a theme-aware secondary content color while preserving the existing link-blue treatment for URLs.
3. Search input, placeholder/clear affordance, and search-history chips/dividers must use theme-aware surfaces/content colors. Light mode must retain the current readable appearance.
4. Avatar and signature editor text fields must use theme resources for their surface, text, and hint colors. Existing editor usability and cursor behavior must remain unchanged.
5. Do not alter behavior, layout, navigation, toolbar white content, or the filter screen's intentional gray helper copy.

## Acceptance Criteria

- [x] In dark Compose mode, every navigation drawer label and normal icon is readable against the drawer background; in light mode the existing visual hierarchy remains readable.
- [x] In dark Compose mode, message author and timestamp metadata are visibly lighter than the background; URL links remain visually distinct and clickable.
- [x] In dark Compose mode, search input/history text and controls have readable contrast without a forced light-only surface; light mode remains readable.
- [x] Avatar and signature editors render readable text and hints in both resource configurations, with no hard-coded black text on a dark surface.
- [x] Existing toolbar, filter helper text, slider, and system-bar contract tests continue to pass.
- [x] Relevant JVM tests and a debug compilation pass; no unrelated files are changed.

## Out of Scope

- Reworking the entire legacy color system or forum HTML user-selected colors.
- Recoloring intentional toolbar white icons/text, status indicators, or the filter screen's two fixed gray helper messages.
- Device screenshot validation or broader UX redesign.
