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

- **NGO authority enforced at the data boundary (publish path)**:
  `NewsRepositoryImpl.publish()` derives `isOfficial` from the local identity's
  alias (`NGO_*` / `*_OFFICIAL` heuristic) and throws `SecurityException` for
  non-NGO callers — the caller-supplied `isOfficial` argument is intentionally
  ignored.  `CommunityBoardRepositoryImpl.setPinned()` and the `pinned=true`
  path of `post()` enforce the same NGO check.  Both ViewModels surface the
  failure via their existing error state.
- **NGO authority enforced at the data boundary (ingest path)** — closes a
  forge-on-the-wire bypass surfaced by the architect:
  - `NewsRepositoryImpl.observeIncoming()` no longer trusts
    `payload.isOfficial` directly. The official bit is now coerced via
    `payload.isOfficial && isNgoAlias(payload.sourceAlias) &&
    isNgoAlias(event.packet.senderAlias)` — both the payload-claimed source
    and the wrapping packet's sender alias must independently pass the NGO
    heuristic, otherwise the post lands as a regular non-official entry.
  - `CommunityBoardRepositoryImpl.observeIncoming()` coerces inbound
    `pinned` to false unless `event.packet.senderAlias` passes the NGO
    heuristic. To make this check meaningful, `post()` now exposes the NGO
    alias as `senderAlias` for **pinned posts only** — the personal CRS ID
    is still never on the wire (an NGO alias like `NGO_OXFAM` is an org
    tag, not a personal identifier, so the spec's anonymity guarantee is
    preserved).
  - `IdentityRepositoryImpl.updateAlias()` now refuses any self-assigned
    alias matching the NGO pattern (`NGO_*` / `*_OFFICIAL`) so a regular
    user cannot trivially elevate themselves by renaming. Real NGO
    provisioning will arrive via a signed onboarding bundle that bypasses
    this guard.
- **Known limit (tracked followup, NOT for this build)**: alias-based NGO
  authority is best-effort defense-in-depth. A determined attacker who can
  craft raw mesh packets and chooses an NGO-pattern alias can still forge
  authority — only cryptographic packet signing (Ed25519 per-NGO key + a
  baked-in NGO trust root) closes that completely. That work is a multi-day
  feature outside the current session's scope.
- **`observeIncoming()` idempotent registration**: `NewsRepositoryImpl`,
  `CommunityBoardRepositoryImpl`, and `DeconflictionRepositoryImpl` guard the
  long-lived event-bus collector with an `AtomicBoolean` so repeat ViewModel
  inits (configuration changes, screen re-entry) cannot register duplicate
  collectors.
- **Destructive Room fallback retained with a note**: `DatabaseModule` keeps
  `fallbackToDestructiveMigration()` as a safety net for the unwritten
  v13→v14 path that already shipped to existing installs.  Comment in code
  flags this as a release blocker for the next non-hackathon build.

## Dead Man Switch — Feature 5 fix pass

Eleven bugs in the Dead Man Switch (Feature 5) were fixed end-to-end so the
feature actually fires once, with the right payload, to the right peers, and
the user is told about it locally:

- **One-shot scheduling.** `DeadManWorker` is now a `OneTimeWorkRequest` with
  `setInitialDelay(intervalMinutes, MINUTES)` instead of a `PeriodicWorkRequest`.
  `DeadManViewModel.scheduleWorker()` enqueues with
  `enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, ...)` so a check-in
  cleanly cancels the in-flight deadline and arms a fresh one. The previous
  periodic config (60-minute floor + repeat) was the wrong primitive entirely.
- **Real escalation list survives the Worker boundary.** `DeadManWorker` now
  serializes the contact list into the `Data` payload via `KEY_CONTACTS` and
  decodes it inside `doWork()`. Previously the worker hardcoded the input to
  `"[]"`, so escalation contacts were silently dropped.
- **Correct on-wire packet type.** Worker now calls
  `PacketFactory.buildDeadManPacket(...)` producing `DEAD_MAN_TRIGGER` packets
  whose body is `DeadManPayload`. `MeshMessenger`'s receiver
  (`MeshMessenger.kt:519`) decodes the same shape, so peers can finally act on
  the alert. Previously the worker built `SOS_ALERT` packets, which the
  receiver path tried to decode as `DeadManPayload` and silently failed.
- **No more ANDROID_ID privacy leak.** Worker reads identity from
  `IdentityRepository.getIdentity().first()` and uses the user's CRS ID and
  alias. The previous code minted a sender ID from `Settings.Secure.ANDROID_ID`
  (a stable cross-app fingerprint) and read alias from raw prefs.
- **Fail-closed if identity isn't bootstrapped.** Worker returns
  `Result.failure()` when identity is null/blank instead of falling back to a
  collision-prone `"local_device"` ID. This prevents two unbootstrapped
  devices from both broadcasting under the same fake ID.
- **GPS attached to the alert.** Worker pulls the last-known location via
  `LocationRepository.getLastKnownLocation()` and includes lat/lon/accuracy +
  timestamp on the payload. The receiver formats this into the local
  notification body so the recipient sees where the sender was last seen.
- **Enriched `DeadManPayload`** — added `senderCrsId`, `senderAlias`,
  `triggeredAt`, `latitude`, `longitude`, `accuracyMeters`,
  `locationTimestamp`. All nullable except sender + timestamp; payload stays
  backwards-compatible with peers running the old shape (extra fields just
  decode as defaults).
- **High-priority local notification when the dead-man fires.** Worker emits
  `NotificationEvent.Sos.IncomingAlert(sosType="DEAD_MAN_TRIGGERED", ...)` on
  the `NotificationEventBus`, which routes through the existing CRITICAL SOS
  channel (DND-bypass, full-screen on Android 11+). The user (and anyone
  holding their phone) now sees the moment the switch goes off — previously
  the fire was silent on the originating device.
- **Real contact picker.** `DeadManScreen` now opens an `AlertDialog` populated
  from `ContactRepository.getFamilyContacts()` with a checkbox per contact,
  showing both label and CRS ID. The `+` button used to silently call
  `availableFamilyContacts.firstOrNull()?.let(::toggleContact)` (an explicit
  `// or mock for demo` no-op).
- **Activate refuses empty contact list.** Tapping Activate with no
  escalation contacts now surfaces an inline error card
  (`DeadManUiState.errorMessage`) with a dismiss button instead of silently
  no-opping.
- **Interval / contact changes locked while active.** `setInterval()` and
  `openContactPicker()` refuse to mutate state while `isActive == true` and
  surface the reason in the error card. The `SettingsSection` is also
  wrapped in `AnimatedVisibility(visible = !isActive)` for visual reinforcement.
- **Stable contact descriptors on the wire.** ViewModel passes
  `"label <crsId>"` to the worker so even if the user edits a contact's
  display name after arming, the broadcast still carries an addressable ID.

Worker injection follows the existing `OutboxRetryWorker` pattern: `@HiltWorker`
with `@AssistedInject`, repos resolved by `HiltWorkerFactory` (already wired
in `CrisisOSApp`'s `Configuration.Provider`). No KSP2 traps introduced — no
default args added on `@Provides` / Retrofit / `@Dao` surfaces.

## Dead Man Switch — UI/UX revamp + inline add-by-CRS-ID

The Dead Man screen was rewritten end-to-end. Two user-facing changes ship
together:

- **Inline "Add Contact by CRS ID" dialog.** The previous picker dialog read
  from `ContactRepository.getFamilyContacts()` and silently no-op'd when no
  family contacts existed (CrisisOS has no Contacts management page). The
  new `AddContactDialog` accepts a CRS ID (required) + label (optional)
  directly from the keyboard. It validates non-blank input, rejects
  duplicates case-insensitively, and surfaces inline errors in the dialog
  body. If the device happens to have family contacts cached, they appear
  as one-tap quick-pick chips below the form. State is held in
  `rememberSaveable` so input survives rotation.
- **ViewModel rename + new method.** `showContactPicker` →
  `showAddContactDialog`; `openContactPicker` → `openAddContactDialog`;
  `dismissContactPicker` → `dismissAddContactDialog`. New
  `addContactByCrsId(rawCrsId, rawLabel)` plus `addContactError` field
  and `clearAddContactError()`. The empty-family-contacts blocking guard
  was removed from `openAddContactDialog` so the dialog is always reachable.
  Persistence model is unchanged: the contact list is still snapshotted
  into prefs + WorkManager input data only at activate(), matching the
  draft-edit-then-arm flow.

UI revamp highlights:

- **Hero timer (260dp ring)** with progress that color-shifts through
  primary → orange → error as the deadline drains; only when armed.
- **Animated status pill** ("● ARMED" with pulsing dot vs "○ DISARMED")
  directly under the timer.
- **Settings as bordered cards** (Interval / Auto-SOS Message / Escalation
  Contacts) in the disarmed state; armed state collapses settings and shows
  an `ArmedSummaryCard` summarising interval, contact count, and message.
- **Modern contact rows** with circular avatar showing the first letter of
  the label (or CRS ID), name + secondary CRS ID, subtle remove button.
- **Empty contacts state** with icon, headline, and inline "Add a contact"
  CTA so users always know what to do next.
- **Sticky bottom CTA** — the activate/deactivate button lives outside the
  scrollable middle area (`Column(weight=1f, verticalScroll)` above), so it
  stays reachable no matter how many contacts the user adds.
- **Gradient activate button** (primary when disarmed, error red when
  armed) with PlayArrow / Stop iconography.

No regressions to Feature 5 internals (one-shot WorkManager scheduling,
DEAD_MAN_TRIGGER packet routing, identity fail-closed, GPS-attached
payload, high-priority local notification) — the revamp is screen-only
plus the renamed/added VM surface listed above. Architect review PASSED
on the revamp diff after fixing one missing `ColumnScope` import flagged
in the first review pass.

## Dead Man Switch — full Feature 5 dump (per CrisisOS_Context.md)

A second, larger pass landed everything Feature 5 promises onto the screen
itself, plus a data-model extension to support phone-number recipients:

- **Phone-number recipients.** `EscalationContact` gained an optional
  `phoneNumber` field. The add-contact dialog now exposes both a CRS ID
  field and a phone field; at least one must be provided. Validation
  rejects duplicates on either CRS or phone (case-insensitive). Persistence
  is forward-compatible — `encodeContacts` only writes the `"phone"` JSON
  key when non-blank, and `decodeContacts` reads it via `optString` so
  older saved state without the key still loads cleanly. `decodeContacts`
  also keeps a contact valid if either channel is non-blank.
- **Composite identity for remove/toggle.** A new `isSameAs` predicate
  matches two contacts when their CRS IDs match (both non-blank,
  case-insensitive) OR their phone numbers match (both non-blank,
  case-insensitive). `removeContact` / `toggleContact` use it instead of
  the previous CRS-only filter, so two distinct phone-only contacts can no
  longer collapse into each other on remove. Architect flagged the bug;
  fix verified.
- **Worker descriptor extended.** Each escalation contact is now
  serialized for the worker as `"label <CRS-XXXX|tel:+91...>"` with
  whichever channels are present. The wire receiver treats the contact
  string as opaque, so peers running the older format keep working.
- **Canonical interval set.** The interval chip row now includes the
  context's canonical 6h / 12h / 24h / 48h options alongside the existing
  short demo intervals (15m / 30m / 1h / 2h / 4h).
- **Updated message placeholder** — uses the exact example from the
  context: "I was heading to Camp B. Contact Mom +91 98765 43210."
- **Pre-deadline reminder note** under the check-in button when armed:
  "You'll get a silent reminder 30 minutes before the deadline."
- **`ArmedSummaryCard` ("WHAT RECIPIENTS WILL RECEIVE")** lists every
  field that goes on the wire when the switch fires: CRS ID, last known
  GPS (with accuracy + timestamp), last camp checked into, the
  pre-written note, and the trigger interval + contact count.
- **`DeliveryChannelsRow`** — four chips for Mesh / Push / SMS / Email
  with availability state. Mesh + Push are marked live; SMS + Email show
  "soon" tags with a caption explaining that the NGO anchor will queue
  them online.
- **`EcosystemTriggerCard`** — explicitly tells the user that firing the
  switch starts a Missing Person search for their CRS ID (Feature 6
  ecosystem trigger from the context).
- **`ResilienceCard`** in the disarmed view — three lines covering all
  three "Loopholes handled" bullets from the context (phone dies, no
  internet since last sync, no connectivity to recipients).

Final architect review PASSED after fixing the composite-identity bug.
Build Android App workflow stays green at the configure phase end-to-end.

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

## Production-readiness sweep — T001-T005 (session plan)

A full execution of the production-readiness session plan landed in this
session. Most of T001-T004 was already implemented in earlier sessions
(DB v15, all entities, all DAOs, all repositories, all packet types,
both new screens, FakeNewsAnalyzer, NGO authority gating, 24h auto-
expiry via `OutboxRetryWorker`); this session closed the remaining gaps
and addressed architect-flagged issues.

### What this session did

- **Removed dead mock seed code from `HomeViewModel`** —
  `triggerMockNotifications()` was defined but never called from any
  Compose screen. It and its now-orphaned `NotificationEventBus`
  injection plus the unused `delay` / `NotificationEvent` / `UUID`
  imports are gone.
- **Bootstrapped mesh-broadcast feed collectors at app scope.** Architect
  found that `NewsRepository.observeIncoming()`,
  `CommunityBoardRepository.observeIncoming()`, and
  `DeconflictionRepository.observeIncoming()` were only being started
  from inside their respective screen ViewModels'`init` blocks. Because
  `EventBus` is a non-replay `SharedFlow` (`replay = 0`), any
  `CRISIS_NEWS` / `COMMUNITY_POST` / `DECONFLICTION_REPORT` packets
  arriving while the user was on Home (or any unrelated screen) were
  silently dropped instead of persisted. All three repositories are now
  injected into `CrisisOSApp` and have their collectors started in
  `onCreate()`. Each repo already has an `AtomicBoolean` re-entry
  guard, so the existing VM-side calls become no-ops once the app
  bootstrap has run.

### Acknowledged debt (deferred for hackathon scope)

These were flagged by architect as production blockers but are out of
scope for the HackIndia 2026 timeline; tracked here so the next pass
can address them:

- **Room migration chain has gaps (1→2, 7→8, 8→9, 11→12, 12→13, 14→15
  only).** v2→v7, v9→v11, and v13→v14 are missing, so Room falls back
  to `fallbackToDestructiveMigration()` for upgrade paths that hit
  those gaps. The destructive fallback is intentional for the hackathon
  build (devices in the demo install fresh from APK), and the comment
  in `DatabaseModule.provideCrisisDatabase` explicitly documents this
  as a release blocker. Before any non-hackathon build, write the
  missing migrations and remove the fallback.
- **NGO authorization is alias-heuristic, not signed.** `isNgoAlias()`
  treats any peer whose alias starts with `NGO_` or ends with
  `_OFFICIAL` as an NGO, both for outbound publishing and for ingest-
  side `isOfficial` / `pinned` enforcement. A peer can spoof an alias
  to escalate. The repos already cross-check the payload-claimed
  source against the wrapping packet's `senderAlias` (so the
  privilege bits get coerced to `false` unless both pass), which is
  the strongest verification possible without a PKI. Replacing this
  with cryptographic NGO signatures is the right next step; tracked
  but out of scope for this session.
- **`SafeZoneRepository.seedDefaultsIfEmpty()` ships six hardcoded
  zones.** This is intentional — the session plan explicitly required
  "drop hardcoded list; observe `SafeZoneRepository.observeAll()` and
  seed Room on first run." The current code does exactly that: seeds
  Room once on first run, then observes Room thereafter. Architect
  flagged it as MEDIUM but it matches the plan's literal specification.

Build Android App workflow stays green at the configure phase end-to-end.

## Checkpoint Intel — Feature 7 revamp

The Checkpoint screen and its data layer were rebuilt end-to-end against
the spec at `CrisisOS_Context.md` lines 352-376.

Domain model:
- `domain/model/CheckpointEnums.kt` introduces `ThreatLevel`
  (SAFE / HOSTILE / UNKNOWN), `DocumentsRequired` (NONE / ID /
  PASSPORT / MULTIPLE), `WaitTime` (UNDER_15M / FIFTEEN_TO_60M /
  OVER_60M / BLOCKED), and `VerificationStatus` (UNVERIFIED /
  CONFIRMED / NGO_VERIFIED). Each has a `fromStorage()` companion
  that decodes unknown future values to the safest default so an
  older client receiving a newer enum value never crashes.
- `Checkpoint` (domain) gained `threatLevel`, `docsRequired`,
  `waitTime`, `verifiedByNgo`, and `lastUpdatedAt`.

Persistence:
- `MIGRATION_15_16` adds the four spec columns to `checkpoints`.
- `MIGRATION_16_17` adds three CSV-encoded TEXT tally columns
  (`threatVotes`, `docsVotes`, `waitVotes`) used by the majority-
  vote aggregator. CSV order matches each enum's declaration order.
- DB version is 17; both migrations are registered in
  `DatabaseModule`.

Aggregation (anti-misuse — Feature 7 spec):
- `CheckpointRepositoryImpl` no longer last-write-wins. Every
  incoming report (local OR mesh) bumps the appropriate index in
  the tally CSVs, and the displayed enum value is recomputed via
  `argmaxIndex()` with tie-break preferring the previously-displayed
  aggregate. A single newcomer cannot flip the threat away from a
  majority.
- Concurrency: a `voteMutex: Mutex` serializes the entire
  read→compute→write cycle across both submission paths so two
  parallel reports cannot read the same baseline tally and lose an
  increment.
- DB write atomicity: `CheckpointDao.applyAggregateUpdate` is an
  `@Transaction` suspend default method that bundles the tally
  update and the `incrementReportCount` into one transaction, so
  `reportCount` and the tallies cannot drift across crash /
  cancellation boundaries.
- Grid-level dedupe: local submissions match by normalized grid
  label first, then by (label + name), so accidentally creating a
  fresh report for the same grid coalesces into the existing row.
- 2-hour TTL: `REPORT_TTL_MS = 2L * 60L * 60L * 1000L`. Stale rows
  are purged by `purgeStaleReports()` on the existing cleanup chain.
- NGO override: `isNgoAlias()` is aligned 1:1 with
  `NewsRepositoryImpl.isNgoAlias()` — strict
  `startsWith("NGO_") || endsWith("_OFFICIAL")`, NOT substring
  matching. An NGO peer flagging a checkpoint SAFE marks it
  `verifiedByNgo = true` for everyone. Same caveat as elsewhere:
  alias-only authority is best-effort defense-in-depth and is the
  right place to slot a future PKI.

UI / UX (CheckpointScreen):
- Privacy banner at the top makes the spec's "no CRS ID is ever
  attached to a checkpoint report" guarantee explicit to the user.
- Threat-color cards (green / amber / red) with a verification
  badge — `NGO VERIFIED` / `CONFIRMED · N reports` / `UNVERIFIED ·
  needs corroboration`.
- `MetaPill` row surfaces wait time, docs required, threat label,
  and a freshness countdown that re-ticks every 60s via
  `produceState`.
- Reroute + Negotiation Script CTAs are gated behind
  `verification != UNVERIFIED` — a single anonymous report cannot
  trigger high-impact actions. A softer "unlock once a second
  report corroborates this" hint replaces them otherwise.
- Bottom-sheet 7-step report flow with chips for each enum, an
  optional anonymous note (no CRS ID stored or transmitted), and
  the freshness disclosure. Sheet state survives rotation via
  `rememberSaveable`.
- "How this feed works" expander explains 1km² grid aggregation,
  2h auto-expiry, ≥2 reports for CONFIRMED, NGO override, and the
  anti-misuse mechanism — exactly mirroring the spec language.
- Reroute CTA navigates to `Screen.Maps`; Negotiation Script CTA
  navigates to `Screen.AiAssistant` with a checkpoint-context
  preset.

Build Android App workflow remains green end-to-end at the configure
phase across all three architect-review iterations.
