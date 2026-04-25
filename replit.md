# Crisis OS — Android Mesh Communication App

## Overview

Crisis OS is an offline-first, decentralized communication and safety platform for Android. It enables peer-to-peer messaging via Bluetooth and Wi-Fi mesh networking (Google Nearby Connections API) without internet or cellular connectivity. Designed for emergencies, natural disasters, and off-grid scenarios.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** Clean Architecture (UI → UseCase → Repository → Data/Device)
- **Dependency Injection:** Hilt (Dagger)
- **Database:** Room (offline-first local persistence)
- **Async:** Kotlin Coroutines + Flow
- **Networking:** Google Play Services Nearby Connections API
- **Maps:** OSMDroid (offline OpenStreetMap)
- **AI:** LiteRT-LM (on-device Gemma 2b/7b)
- **Build System:** Gradle with Kotlin DSL + Version Catalog (`gradle/libs.versions.toml`)
- **Min SDK:** 26 | Target SDK: 35 | Compile SDK: 37

## Project Structure

```
app/src/main/java/com/elv8/crisisos/
├── ui/          - Jetpack Compose screens and ViewModels
├── domain/      - Use cases, repository interfaces, business logic
├── data/        - Repository implementations, Room DAOs, mesh data
│   └── remote/mesh/  - MeshConnectionManager, MeshMessenger (P2P core)
├── core/        - EventBus, notifications, AI context, diagnostics
└── device/      - Hardware: audio, file management, media
```

## Key Features

- Offline mesh networking (multi-hop message propagation)
- SOS alerts, Dead Man Switch, missing person/child alerts
- Supply request tracking
- Offline maps (OSMDroid) with danger/safe zones
- On-device AI assistant (Gemma, fully offline)
- Voice messages, photos, and videos over mesh
- Room database for persistent offline storage

## Replit Environment Notes

- **Java Runtime:** GraalVM 22.3.1 (JDK 19) — installed via Replit modules
- **Build:** Gradle 9.4.1 via `./gradlew`
- **Android SDK:** Not available in Replit — full APK builds require Android Studio or CI/CD with Android SDK
- **gradle.properties:** Fixed Windows-specific `org.gradle.java.home` path (removed), auto-detection enabled
- **Workflow:** Console workflow runs `./gradlew tasks --all` to show available build tasks

## Building the APK

To build and run this app, use **Android Studio** on a local machine:

1. Clone the repository
2. Open in Android Studio (latest stable)
3. Sync Gradle dependencies
4. Connect a physical Android device (mesh features require real hardware)
5. Run the application

Alternatively, use a CI/CD system (GitHub Actions, Bitrise) with an Android SDK environment.

## Architecture Notes

- Clean Architecture with strict layer separation
- Hilt provides dependency injection across all layers
- WorkManager handles background retries, cleanup, and Dead Man Switch monitoring
- Kotlinx Serialization (JSON) used for packet serialization over mesh

## Offline Maps (OSMDroid)

Lives in `core/map/` (MapViewFactory, MapOverlayManager, MarkerFactory,
MapConfiguration) and `ui/screens/maps/` (MapsScreen, MapsViewModel,
CrisisMapView).

OSMDroid is initialized once in `CrisisOSApp.onCreate()` with a custom user
agent and an in-app tile cache (500 MB) so OSM doesn't block requests and
tiles persist offline.

Pin system (per CrisisOS spec):
- **Inner fill** = type color (CAMP / HOSPITAL / WATER / FOOD / EVAC / SAFE_HOUSE)
- **Outer ring** = status color (green=open, orange=near full, red=full/closed)
- Status is derived from `isOperational` + occupancy ratio in
  `SafeZone.status()` — single source of truth shared by pins, list cards, and
  the detail sheet.

Map UX:
- Auto-centers on the user's first GPS fix at street zoom, then never auto-pans
  again — the user is in control. A locate-me FAB animates back to their
  position on demand.
- Distance from user is computed via Haversine in the ViewModel; the list view
  is sorted by raw kilometers, not by parsing display strings.
- A polyline is drawn from the user to the **nearest open** safe zone as a
  visual route hint (true offline routing is a roadmap item).
- Top-left legend explains the status colors; top-right "NEAREST OPEN" badge
  is tappable to jump to that zone; bottom-left shows offline/online state.
- Tapping a map pin opens the same `ZoneDetailSheet` as the list view.

### Danger Zones + Routing Avoidance + Camp Capacity (Feature 2)

- **Aggregation** — `DangerZoneRepositoryImpl.aggregateForMap()` groups
  crowdsourced reports into 1 km² grid cells over a 2 h sliding window. A cell
  with **3+ unique reporters** (counted by sender CRS ID) becomes a CRITICAL
  red circle (600 m); 1–2 reports stay as orange "Unverified" (300 m). ACLED
  events render at 800 m, darker red. Crowd reports auto-expire after 6 h;
  ACLED rows live 7 d and refresh on the next pull. No schema change — the
  source is encoded via `reportedBy = "ACLED"` plus read-side grouping.
- **ACLED sync** — `syncFromAcled(country, lookbackDays)` is fired once per
  process from `MapsViewModel` after the first GPS fix.
  `guessCountryForLocation()` is a coarse first-pass mapper.
- **Routing avoidance** — `MapsViewModel.pickNearestAvoiding()` runs a
  planar segment-vs-circle test against confirmed-red zones and picks the
  closest open safe zone whose direct line does NOT cross any red circle. If
  every option is blocked it falls back to the closest. The "NEAREST OPEN"
  badge shows a `REROUTED · NEAREST OPEN` label + "Avoiding red zone on
  direct route" subline when this happens.
- **Report Danger FAB** — bottom-right red `SmallFloatingActionButton` calls
  `reportDangerHere()`, which writes a row stamped with the user's
  Settings.Secure.ANDROID_ID as `reportedBy` and broadcasts a
  `DANGER_REPORT` mesh packet. A toast confirms.
- **Mesh ingest** — `observeIncomingReports()` is idempotent (a `@Volatile
  observerStarted` flag) and consumes peer `DANGER_REPORT` packets, stamping
  them with the originating sender's CRS ID so peer reports also count toward
  the 3-confirmation threshold.
- **95% capacity broadcast** — `SafeZoneRepositoryImpl.updateOccupancy()`
  emits `AppEvent.CapacityEvent.CampNearCapacity` when an NGO update crosses
  `NEAR_CAPACITY_THRESHOLD = 0.95f`. `MapsViewModel` collects this and shows
  a top-center red banner ("Camp X near capacity · redirect incoming
  arrivals") that auto-dismisses after 30 s.
- **Overlay rendering** — `MapOverlayManager.setDangerZones(...)` draws each
  zone as a translucent filled `Polygon.pointsAsCircle`, prefixed `DANGER_`
  in the overlay map. Each circle is inserted at index 0 of `mapView.overlays`
  via `addOrUpdateOverlay(..., atBottom = true)` so danger circles always
  layer **below** safe-zone pins, the user-location marker, and the route
  polyline — even when an ACLED sync resolves after pins are already on the
  map. Legend gained a separate "DANGER" group with red and orange entries.

### Architect review — Feature 2 fixes applied

- **Capacity event fires only on the rising edge.** `SafeZoneRepositoryImpl
  .updateOccupancy()` reads the prior occupancy ratio, runs the DB update,
  then emits `CampNearCapacity` only when `before < 0.95 && after >= 0.95`.
  Restricted to `SafeZoneType.CAMP` so non-camp zones (hospitals, etc.) don't
  trigger the camp-overflow banner. Prevents repeated emissions on every
  subsequent NGO update at >= 95%.
- **Capacity collector no longer blocks.** `MapsViewModel`'s `eventBus.events
  .collect { ... }` no longer holds a 30 s `delay()` inline. Each banner
  spawns its own child coroutine for the auto-dismiss timer, keyed by the
  hint's timestamp so a fresh banner from another camp is never wiped by an
  old timer.
- **Z-ordered overlay registration.** `MapOverlayManager.addOrUpdateOverlay()`
  gained an `atBottom: Boolean` flag; `setDangerZones()` uses it so danger
  circles always render under pins regardless of which `LaunchedEffect` fires
  first.
- **Atomic idempotency guard.** `DangerZoneRepositoryImpl.observeIncoming
  Reports()` now uses `AtomicBoolean.compareAndSet(false, true)` instead of a
  `@Volatile` flag so two concurrent ViewModel inits cannot double-subscribe.
- **Pseudonymous reporter id.** Crowdsourced danger reports are stamped with
  the local CRS ID from `IdentityRepository.getIdentity()` (the same
  pseudonymous identifier used everywhere else on the mesh) instead of
  `Settings.Secure.ANDROID_ID`. ANDROID_ID is a persistent cross-app device
  identifier and would have leaked the device across peers and the local
  Room mirror; CRS IDs do not. Falls back to a `"local_device"` literal only
  before identity setup completes.

## On-Device AI Assistant (Gemma 4 E2B via LiteRT-LM)

Lives in `core/ai/GemmaInference.kt`, `ui/screens/aiassistant/AiViewModel.kt`,
and `ui/screens/aiassistant/AiAssistantScreen.kt`.

Performance design:
- CPU backend (Adreno 710 GPU buffer can't fit Gemma's 304MB vision component)
- Engine kept warm across messages; `resetConversation()` rebuilds only the
  Conversation when the user starts a "New chat", not the heavy Engine.
- Per-response performance metrics (first-token latency, tokens/sec) are
  bundled into a single `ResponseCompletion.Success(metrics)` event so they
  always attach to the correct streamed message — no race between metric and
  completion streams.
- Active context (`AiContextGatherer`) is injected only when it changes,
  saving prompt tokens on follow-up turns.

Features:
- Streaming markdown responses with delta extraction
- Hands-free TTS read-aloud (critical for checkpoint negotiation use case)
- Voice input via Android `RecognizerIntent` speech-to-text
- Copy-to-clipboard, regenerate-last-response, new-chat actions
- Categorized prompt presets (Medical / Checkpoint / Legal / Survival / Signal)
  with multilingual de-escalation scripts
- App-action tags `[ACTION:SUPPLY_REQUEST|OFFLINE_MAPS|MISSING_PERSON|SOS|
  CHECKPOINT_INTEL|CRISIS_NEWS]` parsed by the ViewModel and surfaced as
  one-tap chips that navigate to the relevant feature

## SOS Broadcast (Feature 4)

Lives in `ui/screens/sos/SosScreen.kt` + `SosViewModel.kt`.

UX flow per CrisisOS spec:
- **Hold-to-broadcast (2s)** with a circular progress ring around the red
  button + haptic kick-off. Releasing early cancels the gesture; reaching 100%
  opens the confirmation dialog.
- **Confirmation dialog** shows exactly what will be sent — own CRS ID, type,
  GPS coordinates (or last-known fix flagged "approximate" when GPS is stale
  >5 min or unavailable), and the message — so the user has one final review
  step before the packet hits the mesh.
- **Auto-repeat every 10 minutes** (`REPEAT_INTERVAL_MS`). The broadcasting
  panel shows a live mm:ss countdown to the next repeat plus elapsed-active
  duration so the user can confirm the alert is still active.
- **10-min cooldown** after the user taps "MARK SAFE & STOP" — sends a
  SOS_CANCEL packet, blocks new broadcasts for `COOLDOWN_MS`, and shows an
  amber banner with a live countdown so accidental retriggers are impossible.
- **Type chips** (Medical / Trapped / Missing / Armed Threat / Fire / General)
  pre-fill the message with a quick phrase the user can edit.
- The viewmodel suppresses its own SOS notification group while broadcasting
  so the user doesn't see a "you sent yourself an SOS" toast loop.

## CRS-ID Lookup (Missing Person + Child Alert merged — Feature 6)

Lives in `ui/screens/missingperson/MissingPersonScreen.kt` +
`MissingPersonViewModel.kt`. The legacy ChildAlert screen has been deleted;
`Screen.ChildAlert` is preserved as a navigation alias that routes to the new
unified screen so notification deep-links keep working.

Two tabs:
- **Search** — pure CRS-ID lookup (regex `^[A-Z]{2,4}-\d{6,8}$`). On submit it
  reads the local Room cache and broadcasts a `MISSING_PERSON_QUERY` packet to
  the mesh. Replies fold in via `AppEvent.MissingPersonEvent.ResponseReceived`.
  After a 4-second timeout with no replies, a "Not found yet" card is shown
  with a Watch CTA so the user can be notified later.
- **Watches** — grouped list of Dependents / Active SOS / Manual watches. The
  viewmodel auto-adds:
  - **DEPENDENT** — from incoming `AppEvent.ChildAlertEvent.AlertBroadcast`
    (the old Child Alert flow now surfaces here).
  - **SOS_AUTO** — own CRS ID when the user broadcasts SOS, so other devices
    that search for them land on a known location.
  - **MANUAL** — added explicitly via the "Add to watch list" CTA on a search
    result.

The unified design absorbs both 6A (Missing Person lookup) and 6B (Child
Alert separation system) without duplicating UI — a child gone missing is
just another watch entry the user is notified about.


## CrisisNews Feed (Feature 11)

Lives in `ui/screens/news/CrisisNewsScreen.kt` + `CrisisNewsViewModel.kt`.
Real Room-backed feed (`NewsItemEntity` / `NewsItemDao`) with mesh propagation
through `MeshPacketType.CRISIS_NEWS` + `NewsItemPayload`. Entries auto-expire
after 24h; the cleanup is driven by the periodic `OutboxRetryWorker` calling
`NewsRepository.purgeExpired()`.

Posting is gated to NGO accounts (alias prefix `NGO_` or suffix `_OFFICIAL`
until a dedicated NGO bit lands on `UserIdentity`). Cards are sorted with
official posts first, then newest-first.

## Community Board (Feature 12)

Lives in `ui/screens/community/CommunityBoardScreen.kt` +
`CommunityBoardViewModel.kt`. Anonymous posts only — `CommunityPostPayload`
carries no CRS ID, the entity stores no author. NGO accounts (same alias
heuristic as CrisisNews) can mark posts pinned. Posts auto-expire after 24h
via the same `OutboxRetryWorker` cleanup path.

## Fake News Detector (Feature 9)

`core/heuristics/FakeNewsAnalyzer.kt` is a deterministic offline scorer that
weighs caps ratio, punctuation density, panic vocabulary, absolute/totalizing
language, urgency markers, propaganda phrasing, and source-citation absence.
**Per spec, it never returns `VERIFIED` offline** — the strongest possible
offline verdict is `UNVERIFIED`. Recent checks persist in
`FakeNewsCheckEntity` and stream back into the UI through
`FakeNewsCheckRepository.observeRecent()`. No `Math.random` / `delay` games
remain in the viewmodel.

## Removed mock seeds (in-VM)

The following hardcoded sample data was removed and replaced with real
persistence-backed flows:
- `MapsViewModel.loadSampleZones()` → `SafeZoneRepository.seedDefaultsIfEmpty()`
  on first run; live updates via `observe()`.
- `DeadManViewModel` Alice/Bob defaults → escalation contacts come from
  `ContactRepository.getFamilyContacts()` and a picker; persisted JSON in
  prefs (`deadman_contacts_v2`).
- `DeconflictionViewModel.loadSampleReports()` → `DeconflictionRepository`
  + `DeconflictionDao`; mesh `observeIncoming()` on init.
- `MissingPersonViewModel.seedWatches()` (Aanya / Ravi) → loaded from
  `ChildRecordDao.getByGuardian(myCrsId)`; empty until the user registers
  dependents.
- `HomeUiState.activeSosAlerts = 2` → derived live from
  `NotificationLogDao.getByType("SOS")` over a 30-minute window.
- `CheckpointScreen` "Heavy document checking / 3 hrs ago" sample report
  history → derived from `checkpoint.reportCount` + `lastUpdated` aggregates.

Mocks that remain are **only** the ones that depend on a server backend we do
not own: server-side Vercel cron for the Dead Man timer fallback, real GDELT
cross-reference, and the bootstrap NGO directory.


## Architect review — addressed fixes (T005)

- **NGO authority enforced at the data boundary**: `NewsRepositoryImpl.publish()`
  now derives `isOfficial` from the local identity's alias (`NGO_*` /
  `*_OFFICIAL` heuristic) and throws `SecurityException` for non-NGO callers —
  the caller-supplied `isOfficial` argument is intentionally ignored.
  `CommunityBoardRepositoryImpl.setPinned()` and the `pinned=true` path of
  `post()` enforce the same NGO check.  Both ViewModels surface the failure
  via their existing error state.
- **`observeIncoming()` idempotent registration**: `NewsRepositoryImpl`,
  `CommunityBoardRepositoryImpl`, and `DeconflictionRepositoryImpl` now guard
  the long-lived event-bus collector with an `AtomicBoolean` so that repeat
  ViewModel inits (configuration changes, screen re-entry) cannot register
  duplicate collectors.
- **Destructive Room fallback retained with a note**: `DatabaseModule` keeps
  `fallbackToDestructiveMigration()` as a safety net for the unwritten
  v13→v14 path that already shipped to existing installs.  Comment in code
  flags this as a release blocker for the next non-hackathon build.

## Online crisis-intel + Firebase wiring

- **Firebase** — `app/google-services.json` ships the live config for project
  `zenith-devs` / package `com.elv8.crisisos`. The `google-services` Gradle
  plugin is applied at the app level, the BoM-based dependencies pull
  `firebase-analytics-ktx`, `firebase-auth-ktx`, and
  `firebase-firestore-ktx`, and `core/firebase/CrisisOSFirebase.kt` is a thin
  Hilt-injected facade that the `CrisisOSApp` class calls on boot to log the
  `app_open` event.
- **GDELT 2.0 DOC API** — public, no auth.
  `data/remote/api/GdeltApi.kt` exposes `searchArticles(query, ...)` and the
  matching DTOs live in `data/remote/api/dto/GdeltDoc.kt`. Wired via Retrofit
  in `di/NetworkModule.kt` against
  `https://api.gdeltproject.org/api/v2/`.
- **ACLED Read API** — auth required.
  `data/remote/api/AcledApi.kt` exposes `readEvents(country, eventDate, ...)`.
  An OkHttp interceptor in `NetworkModule` automatically attaches `email` +
  `key` from `BuildConfig.ACLED_EMAIL` / `BuildConfig.ACLED_KEY`. Those
  values are wired through `app/build.gradle.kts`'s `buildConfigField`,
  which reads from `local.properties` first and falls back to environment
  variables (`ACLED_EMAIL`, `ACLED_KEY`) so credentials are never committed
  to source.
- **CrisisIntelRepository** — `data/repository/CrisisIntelRepository.kt`
  combines both clients behind safe `Result<T>` returns, so callers stay
  offline-tolerant: any network failure degrades silently to "use the
  offline analyzer's verdict only".
- **Fake News Detector cross-reference** — `FakeNewsViewModel.analyzeClaim()`
  now queries GDELT for the top 3 matching domains and merges them into the
  `sources` list of the offline verdict. The offline heuristic remains the
  authoritative verdict per spec; GDELT only adds context.

### Building the APK

The Replit Nix environment does **not** ship the full Android SDK
(`platforms-android-37` + `build-tools` + `cmdline-tools`), so
`./gradlew assembleDebug` cannot run here. The current workflow
(`./gradlew tasks --all`) validates the Gradle config + dependency graph,
which is the strongest check this environment supports. To produce an APK:

1. Open the project in Android Studio (or any machine with the SDK
   installed under `ANDROID_HOME`).
2. The `local.properties` file is generated automatically with the ACLED
   credentials and base URLs — keep it out of source control (the repo's
   `.gitignore` already excludes it).
3. Run `./gradlew :app:assembleDebug` — the resulting APK will land at
   `app/build/outputs/apk/debug/app-debug.apk`.

## KSP2 "unexpected jvm signature V" — root cause + fix

Real root cause (diagnosed after two earlier attempts):
**The two new `@Dao` interfaces from the T003 / T004 work
(`NewsItemDao`, `CommunityPostDao`) declared default arguments on
abstract methods** — e.g. `fun getAllActive(now: Long = System.currentTimeMillis())`
and `suspend fun deleteExpired(now: Long = System.currentTimeMillis())`.
KSP2's Analysis API on Kotlin 2.2.10 + Room cannot generate a JVM
descriptor for the synthetic `*$default` helper that Kotlin emits for
default-valued parameters on abstract interface members, and crashes
with the cryptic `unexpected jvm signature V` (where `V` is the void
return descriptor of that helper). Every other DAO in the project
(`OutboxDao`, `MediaDao`, `MessageRequestDao`, …) takes the same
parameters explicitly, which is why nothing else broke.

Forcing KSP1 isn't an option anymore — `ksp.useKsp2=false` is rejected
by KSP 2.3.x.

Fix applied:

- `NewsItemDao` and `CommunityPostDao` no longer declare default arguments
  on their abstract methods. `getAllActive()` and `deleteExpired()` now
  require `now: Long` explicitly. Callers in `NewsRepositoryImpl` and
  `CommunityBoardRepositoryImpl` were updated to pass
  `System.currentTimeMillis()` at the call site.
- The same pattern was scrubbed from the new Retrofit interfaces
  (`GdeltApi.searchArticles`, `AcledApi.readEvents`). The interface
  methods now take every parameter explicitly, and a Kotlin extension
  function with the same name on the receiver type provides the previous
  default-value ergonomics for callers — extensions live outside the
  KSP-processed surface, so they're safe.

Defensive cleanups carried over from the prior round (not the root cause
but kept for resilience against future Kotlin/KSP bumps):

- `CrisisIntelRepository` returns plain `List<…>` (empty on failure)
  instead of `Result<List<…>>` (avoids the `kotlin.Result` inline-class
  trap on Hilt-processed classes).
- `CrisisOSFirebase` no longer uses `by lazy`, and `logEvent` has two
  explicit overloads instead of a default `Map` argument.
- `NetworkModule` builds the `Json` instance and the OkHttp auth
  interceptor as locals inside `@Provides` instead of object-level
  property initializers.

> The `Build Android App` workflow only runs `gradlew tasks --all`, which
> stops before `kspDebugKotlin`. Validating that the KSP fix sticks
> requires running `./gradlew :app:assembleDebug` (or
> `:app:kspDebugKotlin`) on a machine with the Android SDK installed.
