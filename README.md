# SevaSetu Patient App (Offline-First Android App)

SevaSetu (सेवा = service, सेतु = bridge) is an **offline-first patient app** designed for government hospitals and primary health centres (PHCs) in Jammu & Kashmir. Patients register without queuing, get an OPD slip with a QR code, follow their queue turn in real time, and are guided step-by-step inside the hospital to the doctor's room—even with **zero internet connection**.

---

## 📌 Table of Contents
1. [Overview & What This App Does](#-overview--what-this-app-does)
2. [Key Workflows](#-key-workflows)
3. [Implemented Features (F01 – F51)](#-implemented-features-f01--f51)
4. [Backend & Website Developer Integration Guide](#-backend--website-developer-integration-guide)
5. [Pending / Future Features](#-pending--future-features)
6. [Building & Running the App](#-building--running-the-app)

---

## 🏥 Overview & What This App Does

In government district hospitals, long queue wait times, complex hospital layouts, and poor network connectivity lead to patient frustration and overcrowded corridors. SevaSetu addresses these challenges:

- **Offline-First Operating Model**: All core features—indoor routing maps, symptom check rules, OPD ticket QR generation, audio guidance, and health history—are stored locally on the phone.
- **Queue Transparency**: Shows real-time queue position ("3 people ahead"), estimated consult window ("Likely between 11:20 and 11:50"), and translated reason chips whenever a patient's position changes.
- **Safety Triage First**: Evaluates critical warning signs (chest pain, breathing difficulty, pregnancy bleeding, fits) before registration; any "Yes" instantly redirects the patient to Emergency (Casualty) without issuing a token.
- **Accessible Indoor Navigation**: Uses Dijkstra shortest-path routing over wall QR stickers (`SS:<hospital>:F<floor>:W<id>`) with an interactive Compose Canvas floor plan renderer, stair-free options, wrong-turn auto re-routing, and lost recovery.
- **Elderly & Low-Literacy Friendly**: High-contrast theme, 4 text scale levels (100% to 200%), picture cards, spoken text-to-speech guidance ("Listen" button), and multi-language support (English, Hindi, Tamil).

---

## 🔄 Key Workflows

```
[1. First Launch]  ──>  Language Choice (EN/HI/TA)  ──>  Privacy & Movement Consent
                                                                    │
                                                                    ▼
[2. Home Dashboard] <─────────────────────────────────  [Active Visit Hero Card]
       │
       ├──> [A. Register OPD] ──> Warning Check ──> Details ──> Symptom Check ──> OPD Ticket Slip
       │                                                                                │
       ├──> [B. I Have My Slip] ──> Enter Token / Scan Slip QR Code ────────────────────┤
       │                                                                                ▼
       ├──> [C. My Turn Queue] <── Foreground Service Alerts (3 ahead & Called) ──> [Called Screen]
       │            │                                                                   │
       │            └──> Pressed "Feeling Worse?" ──────────────────────┐               ▼
       │                                                                │       [D. Indoor Navigation]
       ├──> [E. Emergency] <────────────────────────────────────────────┴───────> Wall Sticker Scanner
       │
       ├──> [F. Health Journey] ──> View Past Consultations & Next Follow-ups
       │
       └──> [G. Simulation Panel] ──> Toggle Offline, Autopilot, Clock Speed & Staff Actions
```

---

## 📋 Implemented Features (F01 – F51)

### 1. Foundation & Design System
- **F01**: Project setup, permissions (`CAMERA`, `VIBRATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`), `H01.json` indoor map assets.
- **F02 / F50**: Compose Material 3 design system, `SevaScaffold`, top bar with Back & Listen buttons, persistent 56dp `EmergencyBar`, 4 text scale levels, high-contrast mode with 2dp solid outlines.
- **F03**: Home screen with active visit hero card, 2x2 action tile grid, and secondary navigation links.
- **F51**: Reusable states (`EmptyState`, `LoadingState`, `ErrorState` with retry, and `OfflineBanner`).

### 2. Data & Offline Simulation Engine
- **F04**: `BackendGateway` interface, snake_case DTO serialization, `FakeBackend` Room simulator, and `NetworkException` for simulated offline mode.
- **F05**: Room database (`SevaDatabase`) holding stored visits, patient profiles, registration drafts, outbox queue items, cached health journeys, and public tables.
- **F06**: Priority-based Outbox queue (`OutboxManager`) with WorkManager sync:
  - Priority 0: Urgent Help flags (immediate dispatch)
  - Priority 1: Priority (yellow) registrations
  - Priority 2: Routine (green) registrations
  - Priority 3: Symptom check records
  - Priority 4: Anonymous indoor scan events
- **F07**: Simulated clock starting at 10:30 AM Asia/Kolkata with 1x, 30x, and 120x speedup options.
- **F08**: Simulation & Demo Operator Panel (offline toggle, clock speedup, autopilot switch, doctor call next, confirm priority, send to emergency, mark flag seen, add fictional patients, load demo patient Kamla).

### 3. Language, Consent & Accessibility
- **F09 / F10**: Language selection screen (English, Hindi, Tamil) with spoken TTS greetings and `backendLanguage()` mapper (`hi` -> `hi`, `ta` -> `en`, `en` -> `en`).
- **F11**: Privacy Consent screen (Terms required; Movement tracking optional).
- **F12**: First-launch public hospital data preloader.
- **F13**: Settings screen with text scaling, high-contrast, speech speed, avoid stairs default, alert toggles, and offline readiness checklist link.
- **F14 / F15**: Android TextToSpeech helper and `TRANSLATION_REVIEW.md` document for Hindi and Tamil native-speaker review.

### 4. Safety, Emergency & Symptom Triage
- **F16**: Offline Emergency screen with steps to Casualty, 112 / 108 ACTION_DIAL buttons, and warning sign list.
- **F17**: Honest 3-state urgent flag lifecycle: *"Not yet received by staff"*, *"Sent to staff"*, and *"Staff have seen your request"* (with 800ms vibration feedback).
- **F18**: Symptom check engine using `symptom_rules_demo-1.json`: evaluates 11 critical warning signs first (any "Yes" redirects to Emergency without a token), followed by routine questions mapping to department (`M`, `P`, `A`, `O`), suggested band (`green` / `yellow`), cluster (`fever`, `respiratory`, `diarrhoea`, `skin`, `injury`), and a 2-4 line doctor note.
- **F19**: "Not sure?" help desk fallback routing.

### 5. Registration & OPD Slip
- **F20 / F21**: Step-by-step registration flow with assisted mode banner, age group & special needs picture cards, district picker, and draft persistence.
- **F22**: Submit logic: online returns live `MyTurnDto`; offline creates local `StoredVisit` with an unconfirmed token (`M-x4F2` marked *"Waiting to be confirmed"*), enqueuing registration in Outbox.
- **F23**: Ticket-shaped OPD Slip with 56sp token display, time window range, provisional band pill, ZXing QR code generation (`SS1:SLIP:<hospital>:<visit_id>`), Google Maps navigation link, and share QR action.

### 6. Queue & Alerts
- **F24 / F25**: My Turn live queue screen showing people ahead, estimated window range, paused doctor status info, and translated reason chips (`last_reason` for 10 reason codes).
- **F26 / F27**: Status handling (`called` -> Called screen with room in huge type; `emergency` -> Emergency screen) and 5-second polling / broadcast flow collection.
- **F28**: Foreground `QueueWatcherService` with ongoing notification *"Watching your queue"*, firing high-priority alerts at 3 people ahead and when called.
- **F29**: "I Have My Slip" screen linking token by text search (with seeded demo tokens `M-017`, `M-011`, etc.) or slip QR scan.
- **F30**: Simulated SMS Inbox screen demonstrating feature fallback for patients without mobile data.

### 7. Indoor Navigation & Wall Scanner
- **F31**: `GraphRouter` Kotlin port of `graph.ts` (Dijkstra algorithm over 31 wall sticker nodes with distance, lift wait penalty +10m, crowd penalty +3m, avoid stairs mode, and emergency mode).
- **F32 / F33**: Destination search and step-by-step guidance with turn directions, translated landmarks, and distance.
- **F34**: Interactive Compose Canvas floor plan renderer (10px = 1m scale), drawing rooms, corridors, highlighted route, and "You are here" dot.
- **F35**: CameraX & ML Kit wall sticker scanner (`SS:<hospital>:F<floor>:W<id>`) + "Simulate scan" node list.
- **F36 / F37**: Wrong turn auto re-routing, 90s lost recovery prompt, and anonymous journey event recording (`entry`, `scan`, `wrong_turn`, `room`, `exit`).
- **F38 / F39 / F40**: Map translations (`H01_i18n_hi.json`, `H01_i18n_ta.json`), help desk fallback for unmapped hospitals (H02/H03), and emergency route integration.
- **F49**: Printable wall sticker sheet preview screen in Demo Panel.

### 8. Care Continuity & Quality
- **F41**: My Health Journey timeline with seeded patient Kamla (3 past visits, next follow-up due status).
- **F42**: Before You Visit screen showing hospital address, facility service status (Lab UP, X-Ray DOWN), and doctor availability.
- **F43**: Static offline Help FAQ cards with Emergency handoff.
- **F44**: Offline readiness checklist screen.
- **F45 – F48**: TalkBack accessibility pass, 360dp text scale validation, `SAFETY_CHECKLIST.md` (S1–S10 passed), unit test suite (`GraphRouterTest` passed 3/3), and signed release build configuration.

---

## 🌐 Backend & Website Developer Integration Guide

When connecting this app to a live backend (Supabase / REST server), replace `FakeBackend` with `RemoteBackend` by changing the Hilt binding for `BackendGateway`.

### RPCs & Endpoints to Sync with Website/Staff Dashboard

| Mobile Gateway Function | Backend RPC / Endpoint | Description / Website Developer Perspective |
|---|---|---|
| `registerVisit(p)` | `rpc("register_visit", { p })` | Registers patient visit. Website dashboard displays new token in doctor's queue column. |
| `getMyTurn(visitId)` | `rpc("get_my_turn", { p_visit_id })` | Returns queue status, people ahead, time window estimate, and `last_reason`. |
| `linkSlip(hospitalId, token)` | `rpc("link_slip", { p_hospital_id, p_token })` | Finds today's visit by token for paper slip holders. |
| `raiseUrgentFlag(p)` | `rpc("raise_urgent_flag", { p })` | Raises urgent help alarm. Triage dashboard shows red flashing banner; tapping **Seen** sets `seen_at` which vibrates patient phone. |
| `getUrgentFlag(id)` | `rpc("get_urgent_flag", { p_id })` | Polling fallback for urgent flag acknowledgment state. |
| `recordSymptomCheck(p)` | `rpc("record_symptom_check", { p })` | Records symptom checks for emergency/help-desk outcomes. Clusters (`fever`, `respiratory`, `diarrhoea`, `skin`, `injury`) feed district outbreak maps. |
| `ingestJourneyEvents(list)` | `rpc("ingest_journey_events", { p_events })` | Batches anonymous scan events (`entry`, `scan`, `wrong_turn`, `room`, `exit`) under random `session_id`. Feeds staff crowd map and wrong-turn heatmaps. |
| `getMyJourney(visitId)` | `rpc("get_my_journey", { p_visit_id })` | Returns patient consultation history & follow-ups set by doctors. |
| `getClock()` | `rpc("get_clock")` | Synchronizes mobile queue time displays with the backend's simulated/server time. |

### Realtime Broadcast Synchronization
Subscribe to Supabase Realtime broadcast channels and refetch `getMyTurn`:
- `visit:<visitId>` — Triggers when staff call next patient (`status: called`), confirm priority, or transfer to Emergency.
- `hospital:<hospitalId>` — Triggers when queue order changes.
- `flag:<flagId>` — Triggers when staff tap **Seen** on urgent flag alarm.

---

## 🔮 Pending / Future Features

1. **Live Remote Backend Wiring**:
   - Replace `FakeBackend` with `RemoteBackend` using `@supabase/supabase-js` or Ktor REST client.
2. **Additional Local Language Audio Files**:
   - Add pre-recorded Kashmiri (`ks`) and Dogri (`doi`) audio files under `res/raw/` for complete regional voice guidance.
3. **Bluetooth Low Energy (BLE) Beacon Indoor Positioning**:
   - Integrate BLE beacon RSSI distance estimation alongside wall QR sticker scanning for automatic indoor position updates.

---

## 🛠️ Building & Running the App

### Requirements
- Android Studio Ladybug or newer
- JDK 17 / Kotlin 2.0+
- Min SDK 26 (Android 8.0) | Target SDK 37

### Build Commands
```bash
# Run unit tests
./gradlew :androidApp:test

# Build Debug APK
./gradlew :androidApp:assembleDebug

# Build Release APK
./gradlew :androidApp:assembleRelease
```
