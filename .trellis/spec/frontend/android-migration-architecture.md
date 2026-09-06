# Android Kotlin, Compose, and MVVM Migration Contract

## Scenario: Incremental Feature Migration

### 1. Scope / Trigger

Apply this contract whenever production Android code is moved from Java, XML
Views, MVP, mutable LiveData, RxJava, global managers, or direct data access
toward Kotlin, Jetpack Compose, and MVVM.

The project uses an in-place vertical-slice strangler migration. Do not create
a second application, bulk-convert Java, or replace the navigation shell before
feature and data contracts are stable.

### 2. Signatures

Every migrated screen separates its Android route from its stateless UI:

```kotlin
@Composable
fun FeatureRoute(
    viewModel: FeatureViewModel,
    navigate: (FeatureDestination) -> Unit,
)

@Composable
fun FeatureScreen(
    state: FeatureUiState,
    onAction: (FeatureAction) -> Unit,
)

class FeatureViewModel(
    private val repository: FeatureRepository,
) : ViewModel() {
    val uiState: StateFlow<FeatureUiState>
}

interface FeatureRepository {
    fun observe(...): Flow<FeatureData>
    suspend fun execute(...): FeatureResult
}
```

The exact methods are feature-specific, but the ownership direction is not.
Java callers may use a thin compatibility adapter over the same Repository
during migration.

### 3. Contracts

#### Target architecture

- New feature production code is Kotlin unless a documented interoperability
  boundary makes retained Java safer or materially cheaper.
- Migrated screens use Compose. `AndroidView` is allowed only through a typed,
  lifecycle-owned wrapper for a justified component such as login WebView or
  rich content.
- A ViewModel exposes read-only immutable `UiState` and accepts typed actions.
- Navigation and transient platform effects belong to the route/UI boundary.
  ViewModels do not call ARouter, display Toasts, or retain Activity, Fragment,
  View, or UI Context references.
- Repositories own product operations, cache policy, and account scoping.
  UI and ViewModels do not access Retrofit, Room, files, Preferences, Cookie
  strings, or global business singletons directly.
- New asynchronous APIs use structured Coroutines and Flow. Rx adapters remain
  only at temporary legacy edges and leave with their final consumer.
- Account-bound operations select an immutable session snapshot before the
  request and retain that identity through parsing, persistence, and delivery.

#### Assets and contracts that must be preserved

- Application ID, signing continuity, version/release identity, and package
  upgrade behavior.
- On-disk databases, preference keys, file names/formats, and user data until
  an explicit tested migration owns each format change.
- Deep links, external intents, route arguments/results, and user-visible
  navigation behavior until typed compatibility adapters replace them.
- NGA request/encoding/parser behavior that remains supported, captured as
  typed contracts and sanitized fixtures rather than undocumented code paths.
- Existing interaction contracts, accessibility behavior, account isolation,
  favorite ordering semantics, drafts, caches, and release safeguards.
- GPL/source provenance, licenses, and required assets/resources.

#### Implementation that may remain in the final product

- Controlled WebView/View interop when the wrapper has typed inputs/events,
  lifecycle cleanup, and the required host/session policy.
- Android resource and platform XML such as manifests, strings, backup/network
  security configuration, shortcuts, and file-provider paths. The goal is to
  retire obsolete layout screens, not every XML file.
- Room, OkHttp/Retrofit, Gradle Groovy scripts, multi-Activity navigation, or
  ARouter when they remain intentional, bounded, and tested. Kotlin + Compose +
  MVVM does not itself require Kotlin DSL, Hilt, single Activity, or Navigation
  Compose.
- Stable Java protocol/parser or platform-interoperability code when a rewrite
  provides no behavioral or ownership improvement. Such exceptions must be
  documented, tested, and hidden behind Kotlin-facing boundaries.

#### Transitional implementation

- Existing Activities, Fragments, ARouter routes, Java facades, XML screens,
  Rx-to-Flow adapters, and legacy renderers may remain only while an active
  consumer or rollback route requires them.
- Do not delete a legacy route in the same step that introduces its new data
  boundary. Remove it only after behavior parity and rollback gates pass.

#### Retirement targets

- Presenter-to-Fragment ownership, legacy MVP contracts, mutable LiveData
  exposure, RxBus/RxLifecycle, ButterKnife, direct UI data access, global
  request-time Cookie lookup, and obsolete layout XML.
- Duplicate legacy/new backends for the same feature. Old and new UI must share
  one Repository contract during coexistence.

#### Branch model

- `main` remains releasable and continues to receive product fixes.
- The recommended migration integration branch is
  `migration/kotlin-compose-mvvm`.
- Implement independently verifiable migration slices on short-lived branches
  based on the integration branch, then merge them into the integration branch
  after their gates pass.
- Regularly merge or rebase `main` into the integration branch at controlled
  checkpoints. Do not defer all conflict resolution until the migration ends.
- Merge release-ready checkpoints from the integration branch back into
  `main`; the integration branch is not a reason to hold every completed slice
  until M7. A checkpoint must build, pass its slice gates, preserve legacy
  fallback where required, and be independently releasable.
- When product fixes and migration work are active at the same time, use
  separate Git worktrees for `main` and the migration integration branch.
  Branches isolate history; worktrees prevent unrelated uncommitted changes
  from following branch switches.
- A branch name does not replace Trellis task boundaries, acceptance criteria,
  or per-slice rollback paths.

#### New features during migration

- New features target the migration architecture once M1 shared boundaries and
  the M2 reference pattern are proven; they do not first establish a second
  legacy data/state path on `main`.
- AI research, sanitized fixtures, threat modeling, and adapter contracts may
  continue before those gates. Production AI Core starts after M1/M2 on a
  short-lived branch such as `feature/ai-byok-core`, based on the migration
  integration branch.
- AI chat/provider configuration may ship before the whole legacy UI is
  migrated. Post summary waits for a stable account-scoped post/read model;
  user analysis waits for the corresponding profile/activity boundary and its
  privacy contract.

**Current-fork exception (2026-09-06):** the explicitly approved
`07-25-nga-android-advanced` slice adds BYOK settings and floor/profile summaries
through the existing Java/Preference navigation. It uses a dedicated model
client, protected configuration store, bounded profile adapter, and testable
request controller. Follow the [AI summary contract](../backend/ai-summary-contract.md)
when maintaining that implemented slice; its approval does not certify M1/M2 or
authorize a broader legacy chat/provider architecture.

### 4. Validation & Error Matrix

| Condition | Required decision |
| --- | --- |
| Java file has mixed UI, network, and persistence responsibilities | Extract a tested boundary first; do not mechanically convert it |
| Existing Compose screen reads a singleton or mutable LiveData | Classify it as architecture cleanup required, not migrated |
| New screen requires an old data source | Add one Repository/adapter shared by old and new UI |
| A persisted schema, key, or file must change | Provide migration and rollback tests before switching writers |
| Deep-link or Activity result behavior is not characterized | Keep the existing route and add contract tests before replacement |
| WebView has no typed host/session/lifecycle policy | Do not treat it as an approved final interoperability exception |
| Last consumer of MVP/Rx/ButterKnife/XML disappears | Remove that legacy dependency and its dead resources in the same slice |
| Single Activity, Hilt, or Navigation Compose is proposed | Require a separate benefit/risk decision; do not bundle it implicitly |
| Stable Java code has a clean tested boundary | Retain it unless conversion has a concrete benefit |
| Migration branch has drifted from `main` | Integrate `main` at a checkpoint before adding another high-risk slice |
| A migration checkpoint is not independently releasable | Keep it off `main`; split it further or preserve a tested fallback |
| Product and migration work need simultaneous uncommitted changes | Use separate worktrees; do not carry one worktree's dirty state across branches |
| A new AI screen would consume legacy global network/account state | Stop at research/contracts until M1/M2 boundaries are available |

### 5. Good/Base/Bad Cases

- **Good**: characterize search-history behavior, place its current preference
  format behind a Repository, migrate its ViewModel to `StateFlow<UiState>`,
  keep ARouter in the route callback, and delete the old path after parity.
- **Base**: retain a controlled login WebView or stable Java parser behind a
  Kotlin contract because replacing it would add risk without improving state
  ownership.
- **Bad**: convert every Java file to Kotlin, rewrite its XML as Compose, keep
  the same singleton/model calls, and call the result MVVM.
- **Bad**: combine data-layer replacement, UI rewrite, single-Activity
  navigation, Hilt adoption, dependency upgrades, and storage migration in one
  branch with one rollback point.
- **Bad**: keep all passing migration slices isolated until M7 and resolve the
  accumulated `main` drift only at the end.

### 6. Tests Required

- Characterization tests for route inputs/results, parsers, persisted formats,
  account/session handoff, and user-visible behavior before replacement.
- Repository/data-source tests for typed success and failure, cancellation,
  account binding, cache policy, and Java/Kotlin adapter parity.
- ViewModel tests asserting immutable state transitions and one-time effects.
- Compose UI tests or focused device checks for loading, content, empty, error,
  restoration, accessibility, and navigation behavior.
- Migration tests for every database, preference, or file-format change.
- Debug and minified release/preview build gates, lint-report inspection, unit
  tests, deep-link checks, and the project device gates before route removal.
- Static checks proving new UI does not directly access Retrofit, Room,
  Cookies, mutable Repositories, or legacy global state.

### 7. Wrong vs Correct

#### Wrong

```kotlin
class TopicViewModel : ViewModel() {
    val topics = MutableLiveData<List<Topic>>()

    fun load() {
        RetrofitHelper.getInstance().service.get(url)
        ARouter.getInstance().build("/article").navigation()
    }
}
```

This preserves global transport, mutable state, and navigation side effects
inside a class merely named `ViewModel`.

#### Correct

```kotlin
class TopicViewModel(
    private val repository: TopicRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TopicUiState())
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    fun onAction(action: TopicAction) {
        // Reduce typed actions and call the repository in viewModelScope.
    }
}
```

The route collects `uiState`, renders a stateless screen, and translates a
navigation effect into the existing ARouter contract until that shell is
separately retired.
