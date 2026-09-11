# Backend Development Guidelines

## Active Contracts

| Guide | Use it when | Authority |
| --- | --- | --- |
| [Android Quality and Instrumentation](./android-quality-guidelines.md) | Building, testing, linting, instrumenting, or releasing Android modules | Project quality gates |
| [Local Android Signing](./local-android-signing.md) | Finding the verified signing configuration or building a signed APK that preserves installed data | Local paths, private storage location, certificate identity, credential loading, and verification |
| [Justwen Network Foundation](./network-foundation-contract.md) | Changing transport, account Cookie handoff, Web login, shared error handling, or the vote bridge | Pinned transport/session compatibility plus project safety boundary |
| [NGA Platform Access Rules](./nga-platform-access-rules.md) | Any code crosses an NGA host, WebView, redirect, upload/media host, session, encoding, logging, or mutation boundary | Mandatory evidence, security, privacy, retry, and validation rules |
| [NGA Platform Operation Registry](./nga-platform-operation-registry.md) | Implementing or reviewing a concrete read, post, upload, interaction, account mutation, message, or notification | Operation IDs and pinned Justwen wire/source facts |
| [THREAD.PAGE Topic Pager Prefetch](./thread-page-prefetch-contract.md) | Changing online topic Pager retention, prefetch planning, request reuse, or foreground/background failure behavior | Current-fork prefetch and final-page freshness contract |
| [THREAD.PAGE Local Page Cache](./thread-page-cache-contract.md) | Changing thread cache eligibility, metadata preparation, or description read-back | Full-thread context, selected-page snapshot, and existing cache format |
| [USER.PROFILE Author Location](./author-profile-location-contract.md) | Changing supplemental author reads, location cache/queue/session control, or floor metadata | Per-delivery enrichment, bounded transport, cache-only readers, and metadata-only updates |

## Pre-Development Checklist

For work that touches NGA platform interaction:

1. Read the access rules and locate the operation ID in the registry.
2. Read the network foundation contract when transport, account/session,
   WebView login, or JavaScript bridge behavior is involved.
3. Keep pinned original behavior, current-fork delta, and desired migration
   behavior separate. The pinned source is compatibility evidence, not an
   official or current API promise.
4. Trace request construction through account selection, encoding, parser,
   error classification, persistence, and UI side effects.
5. Use offline fixtures/fake servers. Live NGA traffic requires a separate
   explicit authorization and stop plan.

The generic directory, database, error, quality, and logging files in this
directory are unbootstrapped templates and are not active project contracts.

All maintained specification prose is written in English. Wire field names and
server messages remain in their original form where needed for compatibility.
