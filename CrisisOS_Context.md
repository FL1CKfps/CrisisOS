# CrisisOS — Complete Product Context Document
### For AI Assistant Reference | Version 1.0 | Team elv8

---

## WHAT IS CRISISOS

CrisisOS is a war-ready civilian survival platform built as a native Android application. It is designed to function when all standard infrastructure fails — no internet, no cell towers, no power grid. It serves civilians, refugees, and NGO/camp operators from a single app install, with the user selecting their mode at onboarding.

**One-line pitch:**
"CrisisOS is the operating system for survival — it works when the internet doesn't, finds your family when they're lost, and connects you to help when everything else has failed."

**Hackathon context:**
Built for HackIndia Spark 6 × NIT Delhi, Open Innovation Track, April 2026.
Team: elv8 (pronounced "elevate") | Team Leader: Akshat Dubey | 4 members total.

---

## THE PROBLEM IT SOLVES

### Scale of the crisis (all figures cited):
- 473 million children live in conflict zones worldwide — more than 1 in 6 children on earth. (UNICEF, December 2024)
- 48.8 million children were displaced by conflict and violence by end of 2024 — nearly tripled from 17 million in 2010. (UNICEF Data Hub, December 2024)
- 17,000 children in Gaza alone classified as WCNSF: Wounded Child, No Surviving Family — unaccompanied, untracked, unreachable. (UNICEF, February 2, 2024)
- Conflict drives 80% of all humanitarian needs worldwide. (World Bank, 2024)
- 32,990 grave violations verified against children in conflict in 2023 — highest since UN monitoring began. (UN Secretary-General Report, 2024)
- "By almost every measure, 2024 has been one of the worst years on record for children in conflict in UNICEF's history." — Catherine Russell, UNICEF Executive Director, December 28, 2024

### Three core failures CrisisOS addresses:
1. **Communication dies first** — Internet shutdowns in Gaza, Sudan, and Myanmar cut off humanitarian coordination for weeks. The first casualty of war is connectivity.
2. **Coordination collapses** — NGOs duplicate supply deliveries to the same areas while other regions receive nothing. No real-time coordination layer exists.
3. **Families disappear** — No systematic offline way to find separated family members. No unified civilian identity system that works without the internet.

### Real story that grounds the product:
Razan was 11 years old. She was with her family in her uncle's house in Gaza when it was bombed in the first weeks of the war. She lost her mother, father, brother, and two sisters. Her leg was amputated. Following surgery, her wound became infected. She was separated from her remaining family — her aunt and uncle — with no way to reach anyone. No map. No signal. No identification. UNICEF found her weeks later, still in shock, crying every time she recalled what happened.
Source: UNICEF, Philippe Duamelle field visit report, Gaza Strip, February 2, 2024.

---

## USER TYPES

CrisisOS has two user types, both accessible from a single app install. The user selects their type at onboarding via a toggle.

### 1. Civilian / Refugee
A person fleeing conflict, displacement, or crisis. Has limited connectivity, limited battery, limited resources. Needs: safe routes, supply access, family connection, medical guidance, communication.

### 2. NGO / Camp Operator
A registered humanitarian organization, camp manager, or aid worker. Has semi-permanent location, access to power, and coordination responsibilities. Gets access to the NGO Dashboard with additional features: incoming supply requests, camp capacity management, missing person registry, deconfliction report generator, volunteer skill board, danger zone verification, and broadcast messaging.

NGO registration requires either:
- A verified NGO access code distributed by CrisisOS, OR
- Submitting an access application for manual review

---

## IDENTITY SYSTEM — CRS ID

Every user gets a unique alphanumeric identity generated at registration. No internet required to generate it.

### Format:
```
[First 2 letters of first name][First 2 letters of last name]-[DDMMYY]
```

### Example:
Akshat Dubey, born 15 March 2007 → **AKDU-150307**

### Why this format:
- Easy to memorize, write on paper, or say aloud in noise
- No email, phone number, or internet required to generate
- Recoverable from memory even if phone is lost or destroyed
- Works across language barriers — purely alphanumeric
- Collision handling: if two users generate the same ID, a single-digit suffix is appended automatically (e.g., AKDU-150307-2)

### Family linking:
During onboarding, users link family members. Children registered by a guardian are linked as dependents:
- Guardian: AKDU-150307
- Child 1: PRDU-220314 → linked to AKDU-150307
- Child 2: RIDU-050318 → linked to AKDU-150307

The link is stored locally and synced to NGO nodes when connection is available.

### Unregistered persons:
NGO staff can register a person manually at any camp node — enter name and date of birth, generate CRS ID, write it down for them. No phone required for the person being registered.

---

## CONNECTIVITY ARCHITECTURE

The app silently monitors connection quality and switches modes automatically. The user never touches a setting.

### Degradation stack (automatic, top to bottom):
```
LEVEL 1: WiFi          → Full sync, all 12 features, real-time data
LEVEL 2: 4G / 3G       → API calls throttled, images compressed
LEVEL 3: 2G / EDGE     → Text-only sync, essential data only
LEVEL 4: Bluetooth Mesh → Full offline DTN mode, all core features
```

SMS was considered but excluded from the current scope. The stack goes directly from cellular to Bluetooth mesh.

### Regional Sharding (scale solution):
A device never caches the entire world. On install:
- App detects GPS coordinates
- Downloads data shard for 50km radius only
- 50km radius in a conflict zone ≈ 50,000 users max ≈ 25MB
- Radius auto-expands to 100km if active conflict is detected nearby
- As the user moves, old region shard drops, new region shard syncs in background
- Cross-region queries (e.g., missing person search) propagate through mesh hop-by-hop

**Scale math:**
- 50,000 users × 500 bytes per record = 25MB per shard
- 500,000 users in a region = 250MB (edge case, still acceptable)
- Multiple countries in conflict = each country is its own shard cluster, zero cross-country pollution

---

## DTN ROUTING — DELAY TOLERANT NETWORKING

This is the backbone that makes all async features work reliably when devices are constantly moving and going offline.

### The protocol:
CrisisOS uses DTN (Delay-Tolerant Networking) — the same architectural concept NASA uses for deep space communication with Mars rovers, where messages must survive hours of signal blackout and uncertain routing paths.

DTN uses a store-carry-forward approach: data is incrementally moved and stored throughout the network until it reaches its destination. No end-to-end path needs to exist at any moment.

### Key insight — NGO nodes are the backbone:
Civilian nodes are unreliable (they move, lose battery, go offline). NGO/Camp nodes are semi-permanent, plugged into power, and act as the reliable backbone of the mesh. Only NGO anchor nodes store breadcrumbs for reply routing. Civilian nodes forward messages but are not relied upon for return paths.

### How a message travels (forward path):
```
Your device → civilian node A → [NGO ANCHOR NODE 1: logs breadcrumb { token: X9K2, from: nodeA }]
→ civilian node B → [NGO ANCHOR NODE 2: logs breadcrumb { token: X9K2, from: NGO1 }] → destination
```

### Reply routing — 3-tier fallback:
**Tier 1 — Breadcrumb trail (fastest):**
Reply follows exact reverse path through all NGO nodes that logged token X9K2. Works if NGO nodes are still online.

**Tier 2 — NGO anchor routing (reliable):**
If some NGO nodes in trail are offline, reply hops between remaining NGO nodes only. Each checks: "did I log token X9K2?" If yes, forward toward origin.

**Tier 3 — NGO mailbox (guaranteed delivery):**
Reply stored at nearest NGO node as a mailbox entry keyed to the recipient's CRS ID. Next time the recipient's device comes within Bluetooth range of any NGO node, it pulls all pending mailbox entries. Works even if the recipient has moved 500km.

### Message TTL and priority:
- Every message has TTL (Time To Live): max 50 hops, expires after 72 hours
- Priority queue (highest to lowest):
  1. SOS broadcast
  2. Dead man's switch trigger
  3. Missing person reply
  4. Supply request confirmation
  5. Checkpoint alerts
  6. Regular mesh chat

---

## ALL 12 FEATURES

---

### FEATURE 1: Supply Request → NGO Matcher

**What it does:** Civilian taps what they need. The request is matched to the nearest capable NGO and confirmed back.

**User flow:**
1. Civilian taps "I need help"
2. Selects category: Water / Food / Medicine / Shelter / Blanket / Emergency / Other
3. Request tagged with CRS ID + last known GPS grid square (not exact coordinates, for privacy)
4. If online: matches against live NGO registry, returns nearest capable NGO with ETA
5. If offline: request enters DTN queue, hops through mesh until it hits an NGO node
6. NGO sees aggregated demand board: "3x water within 2km, 1x medical"
7. NGO taps "Accept" → civilian receives confirmation with meeting point and ETA
8. On fulfillment, NGO marks complete → request closes

**NGO dashboard view:**
- Live incoming request feed by category and distance
- One-tap accept/decline per request
- Bulk accept for same-category requests in same area
- Fulfillment history log

**Loopholes handled:**
- No NGO nearby: request stored in DTN buffer for up to 72 hours. Civilian sees "Request queued — will notify when help is available." Never a silent failure.
- Duplicate requests: same CRS ID, same category, within 2 hours = de-duped automatically
- Fake requests: NGOs can flag abuse. After 3 flags, CRS ID is soft-blocked from supply requests for 24 hours (not from other features)

**Ecosystem trigger:** Supply request creation also resets the Dead Man's Switch timer by 30 minutes, since the user is clearly active.

---

### FEATURE 2: Offline Maps + Safe Zones + Danger Zones + Camp Capacity

**What it does:** Full offline map showing safe camps, danger zones, checkpoint statuses, and routing that avoids red areas.

**Data sources:**
- Map tiles: OpenStreetMap (pre-downloaded for regional shard, free, no license)
- Camp locations: UNHCR public database (seeded on first sync)
- Danger zones: ACLED conflict data (updated every sync, last known data used offline)
- Camp capacity: Updated live by NGO staff via their dashboard slider

**Map pin system:**
- Green: Camp/shelter — open, space available
- Yellow: Camp/shelter — open, near capacity (>70% full)
- Red: Camp/shelter — full OR active danger zone
- Blue: Hospital / Medical point
- Purple: Supply distribution point
- Orange: Unverified danger report (<3 confirmations)

**Routing:**
- Auto-generates route to nearest green pin
- Route avoids all red zones automatically
- Re-routes in real time as zone statuses change
- Offline routing uses last cached zone data

**Crowdsourced danger zones:**
- Any user can report a new danger zone anonymously
- Reports from 3+ unique CRS IDs in same 1km² grid within 2 hours → auto-flags red
- Single report → unverified orange pin only
- NGOs can verify or dismiss unverified reports
- Reports auto-expire after 6 hours

**Camp capacity:**
- NGO staff update via simple slider: "current occupancy: 340/500 — 68%"
- No external API — NGOs are the data source
- If camp hits 95% → nearby camps get a broadcast: "Camp A near capacity, redirect incoming"
- Capacity is cross-checked against check-in count from the Missing Person system

---

### FEATURE 3: Bluetooth Mesh Chat

**What it does:** Encrypted messaging between users with no internet required. Messages hop device-to-device.

**Technical basis:** Direct port of the team's existing Relay project, which reached Top 10 at Build With Trae Hackathon, NIT Delhi, March 2026.

**How it works:**
- Messages encrypted end-to-end using recipient's public key derived from their CRS ID (libsodium, X25519 key exchange, ChaCha20 encryption)
- Each message has TTL: max 50 hops, expires in 72 hours, prevents infinite loops
- Group broadcast: send to "all users within N hops" for emergency announcements
- NGO nodes act as high-power relay points with persistent storage

**Connectivity modes:**
- WiFi available → standard internet messaging
- Cellular available → standard internet messaging
- Bluetooth only → full mesh routing via DTN

**Battery optimization:**
- Background mode: Bluetooth scans 2 seconds every 30 seconds (duty cycling)
- Active mode (app open or SOS active): continuous scan
- Low battery (<15%): switches to receive-only mode, stops forwarding for others

---

### FEATURE 4: SOS Broadcast

**What it does:** One button sends an emergency alert to everyone in mesh range and all registered NGOs.

**User flow:**
1. Hold red SOS button for 2 seconds (prevents accidental activation)
2. Confirmation dialog: "Send emergency alert?" [Cancel] [SEND SOS]
3. On confirm: broadcasts CRS ID + GPS grid square + timestamp
4. Priority-1 in DTN queue — jumps all other traffic
5. All NGO nodes within mesh range receive immediately
6. Nearby civilians (within 5 hops) get notification: "SOS from AKDU-150307, ~800m away"
7. NGO dashboard shows flashing SOS pin on map
8. SOS auto-repeats every 10 minutes until user cancels or NGO marks "responding"

**Loopholes handled:**
- False SOS spam: 10-minute cooldown. 3 false SOS flags from NGOs = 1-hour soft block
- User unconscious and can't cancel: SOS keeps repeating — correct behavior
- GPS unavailable: broadcasts last known GPS + "location approximate" flag

**Ecosystem trigger:** SOS creation automatically opens an Emergency supply request on behalf of the user AND creates a watch on their CRS ID for the missing person system.

---

### FEATURE 5: Dead Man's Switch

**What it does:** If the user doesn't check in within a set time, family automatically receives their last known location and a pre-written message.

**Setup:**
- User sets interval: 6h / 12h / 24h / 48h
- Writes optional pre-composed message: "I was heading to Camp B. Contact [name] at [number]."
- Designates recipient CRS IDs or phone numbers
- Timer is SERVER-SIDE — not dependent on device being online

**Flow:**
1. Timer starts on server
2. 30 minutes before deadline: silent push notification to user
3. User taps "I'm okay" → timer resets
4. If no check-in by deadline → server fires message to all designated recipients
5. Message contains: CRS ID, last known GPS, last camp checked into, pre-written note, timestamp
6. Delivered simultaneously via: app push notification + SMS + email
7. Missing person search automatically initiated for that CRS ID

**Loopholes handled:**
- Phone dies before deadline: Timer is server-side, fires regardless of device state
- No internet since last sync: Server uses last sync timestamp. If device hasn't synced longer than the set interval, server fires anyway
- No connectivity to recipients: Message queued and delivered via all available channels

**Ecosystem trigger:** Dead man's switch firing automatically initiates a Missing Person search (Feature 6) for the user's CRS ID.

---

### FEATURE 6: Missing Person Finder + Child Separation Alert

#### 6A: Missing Person Finder

**What it does:** Report a missing person by their CRS ID. Query propagates through the mesh. When the person is found, the reply routes back.

**Search flow:**
1. User taps "Find Person" → enters CRS ID (e.g., PRDU-220314)
2. App generates search query: `{ target_id: "PRDU-220314", requester_id: "AKDU-150307", token: "X9K2", timestamp, TTL: 50 }`
3. Query broadcasts through mesh
4. Every node checks its local cache: "do I have PRDU-220314?"
5. If yes: generates reply with location + last seen timestamp
6. If no: forwards query and logs breadcrumb
7. Reply routes back via 3-tier DTN system (described in Architecture section)
8. User receives: "PRDU-220314 last seen at Camp B node, 23km north, 2 hours ago"

**Cache update triggers (passive location tracking):**
- Person checks into a camp → CRS ID + location stored at NGO node
- Person sends any message through mesh → logged at relay nodes
- Person uses supply request → CRS ID + location logged
- Passive: any time device is in Bluetooth range of any node → presence logged

**De-duplication:** Multiple people searching for same CRS ID = one active search. All requesters receive the same reply.

**Loopholes handled:**
- Target never registered: Reply "CRS ID not found. Visit nearest camp to register." NGO staff can register them on the spot.
- Target moved from last seen location: System returns last known location + timestamp so searcher can reason about current location
- Query never answered: After TTL expires (72 hours), requester is notified "search expired, no match found — try again"

#### 6B: Child Separation Alert (Automatic)

**Trigger condition:** A CRS ID tagged as a minor/dependent checks into a camp node WITHOUT their linked guardian CRS ID also present at that camp within a 2-hour window.

**Automatic flow:**
1. Child CRS ID PRDU-220314 checks into Camp B
2. System checks: is PRDU-220314 linked to a guardian? Yes: AKDU-150307
3. Has AKDU-150307 checked into Camp B in last 2 hours? No
4. Alert fires automatically:
   - Camp NGO staff notified: "Unaccompanied minor checked in — PRDU-220314"
   - Guardian AKDU-150307 notified via DTN: "Your dependent PRDU-220314 is at Camp B unaccompanied"
   - Child flagged in camp system for extra supervision
5. Zero internet required at any step

**Pre-authorization:** Guardian can set "separated travel" mode for up to 24 hours to suppress false alerts when they've intentionally sent the child ahead.

---

### FEATURE 7: Checkpoint Threat Intelligence

**What it does:** Crowdsourced real-time reports of checkpoint conditions — safety status, documents required, wait time.

**Report flow:**
1. User arrives at checkpoint → taps "Report Checkpoint"
2. Selects checkpoint on map or pins current location
3. Fills: Safe / Hostile / Unknown
4. Documents asked for: None / ID / Passport / Multiple
5. Wait time: <15min / 15-60min / 1hr+ / Blocked
6. Optional anonymous text note
7. Submits — no CRS ID attached to this report

**Data aggregation rules:**
- Reports aggregated at 1km² grid level — no exact coordinates stored
- Status shown = majority vote of reports in last 2 hours
- Reports older than 2 hours auto-expire (stale intel is dangerous)
- Minimum 2 reports for "confirmed" status — single report shows "unverified"
- NGOs can override and mark a checkpoint "verified safe" from their dashboard

**Routing integration:** If user is approaching a hostile checkpoint, app automatically offers an alternative route.

**LLM integration:** If checkpoint is reported hostile and user opens the LLM assistant, it auto-surfaces: "Checkpoint ahead reported hostile. Want a negotiation script?"

**Enemy misuse prevention:** Reports show checkpoint STATUS only. Grid-level aggregation prevents civilian movement pattern analysis. No data reveals who reported or how many people crossed.

---

### FEATURE 8: On-Device AI Assistant — Gemini Nano

**What it does:** An AI assistant that works with zero internet. Handles medical triage, checkpoint negotiation, first aid, legal rights, and general crisis guidance.

**Model:** Gemini Nano — built into Android 9+, zero download, zero API key, zero billing. Runs on the device's Neural Processing Unit. Fallback: ONNX Runtime with Phi-3 Mini quantized (optional download on higher-end devices).

**Pre-loaded static knowledge base:**
- WHO first aid and triage protocols
- MSF field medicine guidelines
- Basic IHL civilian rights (Geneva Conventions simplified to plain language)
- Checkpoint negotiation phrases in 6 languages: Hindi, English, Arabic, Ukrainian, Russian, French
- Common war propaganda and manipulation detection patterns

**Core use cases:**

Medical triage:
```
User: "My child has high fever and diarrhea for 2 days"
LLM: "Priority: HIGH. Likely dehydration risk.
1. Give ORS (oral rehydration salts) every 5 minutes
2. Keep child cool, not cold  
3. Get to medical point within 4 hours
[TRIAGE GUIDANCE ONLY — seek medical professional immediately]"
```

Checkpoint negotiation:
```
User: "I'm at a checkpoint, soldier speaks Arabic, I don't understand"
LLM generates script in Arabic:
"Ana muwatin madani. Ma'i atfali. Nahnu dhahibun ila Mukhayyam B."
(I am a civilian. I have my children. We are going to Camp B.)
[Reads aloud via TTS — hands-free]
```

Legal rights:
```
User: "They took my documents, what are my rights?"
LLM: "Under Article 27 of the Fourth Geneva Convention..."
```

**Hallucination safeguard:** All medical outputs carry mandatory disclaimer. Framed as "triage priority guidance" not diagnosis. Life-threatening situations always direct to professional help. Offline mode labeled clearly.

**Ecosystem integration:** LLM is context-aware. Hostile checkpoint nearby → auto-surfaces negotiation script. Supply request pending → helps draft more detailed request description.

---

### FEATURE 9: Fake News Detector

**What it does:** Paste a headline, share an image, or describe a rumor. App returns a credibility score.

**Online mode:**
- Queries GDELT API in real time — cross-references claim against all major news outlets globally
- Reverse image search + metadata forensics (GPS/timestamp inconsistencies)
- Output: "HIGH CREDIBILITY — confirmed by Reuters, AP (2 hours ago)" OR "LIKELY FALSE — image dates to 2019 conflict"

**Offline mode:**
- Gemini Nano scans for: extreme emotional language designed to cause panic, logical inconsistencies, patterns common in war propaganda
- Output: "⚠️ BASIC SCREENING ONLY (offline) — Contains high-emotion language, verify when connected"
- Never shows "TRUE" label offline — overclaiming accuracy offline is more dangerous than showing uncertainty

---

### FEATURE 10: Deconfliction Report Generator

**Background (important for judges):**
Under the Geneva Conventions and Additional Protocol I (Articles 18-23 and related provisions), civilian sites — hospitals, refugee camps, food distribution points, water sources — can be formally registered as protected locations under International Humanitarian Law. This process is called "deconfliction." It requires NGOs to submit precise GPS coordinates, site type, and contact information to military commands and UN OCHA liaisons BEFORE any incident occurs.

**The problem it solves:**
This process currently happens over email and fax. MSF hospitals in Kunduz (2015), Aleppo (2016), and 36 healthcare facilities in Gaza (2024) were struck partly because deconfliction paperwork was delayed, lost, or never reached the right command level. The paperwork that was supposed to protect them failed because the process is analog and slow.

**What CrisisOS builds:**
1. NGO staff opens Deconfliction module
2. Enters: site name, type, GPS coordinates (auto-filled from device GPS), capacity, contact person, Geneva Convention protection category (auto-suggested based on site type)
3. App generates a PDF report in UN OCHA standard format in under 60 seconds
4. Report is SHA-256 hashed → timestamp logged locally + on server when online
5. One-tap share: email / WhatsApp / direct to pre-registered military liaison contacts
6. Hash stored permanently — tamper-proof evidence the report was filed before any incident

**Why judges react to this:** Most people have never heard of deconfliction. When explained in context of real hospital bombings, it becomes the most memorable feature in the pitch.

---

### FEATURE 11: CrisisNews Feed

**What it does:** Hyperlocal conflict news for the user's exact GPS region, delivered even without internet.

**Online:**
- Pulls from GDELT, ACLED, and verified NGO announcements
- Covers: evacuation orders, camp openings, danger zone updates, aid distribution schedules, medical point locations
- Filtered to user's regional shard — no irrelevant global news

**Offline (mesh distribution):**
- News headlines cached by nodes that recently had internet
- Headlines forwarded through Bluetooth mesh to nearby devices
- Users receive news on Bluetooth — no internet required
- NGOs can push verified announcements to all users in their mesh range

**Ecosystem integration:** Danger zone updates from CrisisNews automatically update the map layer. Evacuation orders trigger a notification overlay on the map.

---

### FEATURE 12: Community Board

**What it does:** Anonymous, mesh-distributed public posting board. Like a civilian Twitter that works entirely on Bluetooth.

**How it works:**
- Anyone can post: checkpoint warnings, supply availability, route conditions, missing person notices, any critical information
- Posts propagate through the mesh anonymously — no CRS ID attached
- No central server — fully distributed
- NGOs can pin verified announcements to the top of the board in their region
- Posts expire after 24 hours to prevent stale data buildup

**Ecosystem integration:** Posts that reference specific locations or checkpoint coordinates auto-feed into the Checkpoint Threat Intelligence layer as additional data signals, weighted lower than direct checkpoint reports but contributing to aggregate confidence.

---

## TECH STACK

### Mobile — Android Native
- **Kotlin** — native Android, not a cross-platform wrapper
- **Google Nearby Connections API** — P2P mesh using BLE + WiFi Direct, Google's own offline device networking
- **Jetpack Compose** — modern declarative UI
- **Room + DataStore** — offline-first local storage, entire regional shard cached on device
- **libsodium via JNI** — E2E encryption, X25519 key exchange, ChaCha20 stream cipher

### On-Device AI
- **Gemini Nano** — built into Android 9+, zero download, zero API cost, runs on device NPU
- **MediaPipe LLM Inference API** — Google's production SDK for running Gemini Nano natively
- Pre-loaded static knowledge base (WHO, MSF, IHL, 6-language checkpoint scripts)
- Voice TTS for hands-free operation

### Backend — Minimal by Design
- **Node.js + Express.js** — lightweight REST API
- **Vercel** — serverless deployment with `fluid: true` enabled (no timeout issues)
- **Supabase** — regional shard sync and auth, Postgres under the hood
- **Vercel Cron Jobs** — server-side dead man's switch timers (fires regardless of device state)

### Free Data Sources (zero cost)
- **ACLED API** — Armed Conflict Location & Event Data, live verified conflict data, used by UN and World Bank
- **UNHCR Open Data Portal** — camp locations globally
- **OpenStreetMap** — offline map tile cache, no licensing fees
- **GDELT Project** — Global Database of Events, Language and Tone, indexes every major news outlet
- **Gemini Nano** — on-device, completely free

**Cost of entire stack at hackathon scale: $0.**

---

## WHAT HAS BEEN BUILT BEFORE (Team History)

The team previously built **Relay** — a Bluetooth mesh messaging app using flutter_blue_plus and ble_peripheral, with AES-256 encryption, ghost node scrubbing, image chunking, and a 10-step build order. Relay reached Top 10 at Build With Trae Hackathon, NIT Delhi, March 28, 2026.

CrisisOS's Bluetooth mesh chat feature (Feature 3) is a port of Relay's core architecture, rebuilt in Kotlin with the Nearby Connections API.

---

## HARDWARE VISION (Future Scope Only — Not Being Built Now)

CrisisOS is pure software for this hackathon. Hardware is Phase 3 future scope only.

### The vision (for roadmap slide):
```
LAYER 1: Phone-to-Phone (always)
Technology: BLE / WiFi Direct via Nearby Connections API
Range: 100-500m
"Every phone running CrisisOS is a node"

LAYER 2: CrisisOS Anchor Node — Phase 2
Technology: LoRa Radio (Meshtastic-compatible, $50 solar device)
Range: 100-150km per node
"NGO camps become permanent mesh backbone nodes"

LAYER 3: HF Radio Bridge — Phase 3
Technology: Shortwave HF Radio / Winlink
Range: 1,000km between camp clusters
"The same technology that kept communication alive in WW2"

LAYER 4: Satellite Uplink — Phase 3
Technology: Starlink emergency API + Othernet broadcast
Range: Global
"Any camp with Starlink = internet gateway for entire surrounding mesh.
Othernet broadcasts CrisisNews to all devices globally, free."
```

**Pitch line for this:** "CrisisOS degrades gracefully from 5G all the way down to shortwave radio. No single point of failure. Ever."

---

## HACKATHON BUILD PLAN

### What is being built (working demo):
- Bluetooth mesh chat + SOS broadcast (Relay port)
- Supply request → NGO matcher
- Offline maps + camp capacity + danger zones
- Dead man's switch (server-side timer via Vercel Cron)
- Missing person finder (DTN query over mesh, NGO mailbox)
- Local LLM triage via Gemini Nano (3-4 preset flows)
- CrisisNews feed (online mode)
- Community Board (basic mesh post propagation)

### What is demo-ready (working UI, seeded/realistic data):
- Fake news detector
- Checkpoint threat intelligence
- Deconfliction report generator
- Child separation alert

### What is pitched as vision (slides only):
- Full DTN regional sharding at scale
- Cross-border mesh federation
- Hardware anchor node layer

---

## JUDGING CRITERIA ALIGNMENT

HackIndia Spark 6 judges evaluate on:
1. Innovation and originality (33%)
2. Technical implementation (34%)
3. Presentation and clarity (33%)

### How CrisisOS scores:
- **Innovation (33%):** Novel combination of DTN routing + on-device AI + IHL legal framework + deconfliction. No comparable product exists.
- **Technical (34%):** DTN architecture, Gemini Nano integration, regional sharding, E2E encryption, NGO anchor node mailbox system — all technically defensible and deep.
- **Presentation (33%):** Real UNICEF data, Razan's real story, Geneva Convention legal grounding, deconfliction as a jaw-drop moment.

---

## DEMO SCRIPT (4 Minutes)

```
0:00 — Open with Razan's story (30 seconds, no product)
"Razan was 11 years old. She was in Gaza when her 
family's house was bombed..."

0:30 — Show mesh chat in airplane mode
Two phones, message hops between them. No internet.

0:50 — Show offline map
Safe camps (green pins), danger zones (red), 
hostile checkpoint (orange). App reroutes automatically.

1:20 — Show missing person search
Enter child's CRS ID → "Located at Camp B, 4km away, 
2 hours ago." Show the DTN breadcrumb explanation briefly.

1:50 — Show SOS + dead man's switch
"She sets a 6-hour check-in. If she doesn't tap 
I'm okay, her sister automatically gets her location."

2:20 — Show supply request
Tap water → nearest NGO sees it → confirms → 
ETA notification. 30 seconds end-to-end.

2:50 — Show deconfliction report (NGO mode)
"The camp she's heading to — its coordinates are 
already registered as a protected site under the 
Geneva Convention. We automated a process that 
currently happens by fax."

3:30 — Close
"CrisisOS works when everything else fails. 
It's not built for normal times. 
It's built for the worst day of your life."
```

---

## KEY PHRASES AND TAGLINES

- "The operating system for survival"
- "Works when everything else fails"
- "Built for the worst day of your life"
- "Every phone running CrisisOS is a node"
- "We automated a process that happens by fax — and gets hospitals bombed when it fails"
- "NASA uses this architecture for Mars rovers. We use it for refugees."
- "CrisisOS degrades gracefully from 5G all the way down to shortwave radio"
- "A 1% deployment is 4.7 million lives"
- "Not an app. An operating system for survival."

---

## SOURCES REFERENCED

- UNICEF Press Release, February 2, 2024 — 17,000 unaccompanied children Gaza, Razan's story
- UNICEF Data Hub, December 2024 — 48.8 million displaced children
- UNICEF, December 28, 2024 — Catherine Russell quote, 473 million children in conflict zones
- UNICEF, December 2024 — 32,990 grave violations against children
- UNHCR Global Trends, 2025 — 122 million forcibly displaced globally
- UNHCR 2024 / 2025 — Sudan displacement figures
- World Bank, 2024 — conflict drives 80% of humanitarian needs
- Access Now / NetBlocks 2024 — internet shutdown data
- MSF field reports — Kunduz 2015, Aleppo 2016 hospital strikes
- Al Jazeera / UNICEF October 2024 — Gaza healthcare facility strikes
- Geneva Conventions and Additional Protocols — IHL basis for deconfliction feature

---

*CrisisOS — Built for the worst day of your life.*
*Team elv8 (elevate) | HackIndia Spark 6 × NIT Delhi | April 2026*
