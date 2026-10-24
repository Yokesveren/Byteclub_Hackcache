# DECISIONS.md

This file records architecture and design choices made during the development of the standalone SevaSetu patient app.

## 1. Standalone Architecture & FakeBackend
- The app operates completely standalone without external network calls.
- `FakeBackend` provides full Room-backed simulation of server behavior, including queue calculations, token generation, wait window estimates, urgent flag tracking, and autopilot queue movement.
- `BackendGateway` interface isolates `FakeBackend` so it can be swapped with a real Supabase/REST remote backend by changing a single Hilt binding.

## 2. Local State & Offline Outbox
- All data is stored locally in Room (`SevaDatabase`) and DataStore.
- `OutboxManager` uses WorkManager to queue and retry offline operations when simulated connectivity changes or periodically. Priority 0 (urgent flags) are dispatched first.

## 3. UI & Accessibility Design System
- Built entirely with Jetpack Compose Material 3.
- Supports 4 text scales (100%, 125%, 150%, 200%) and a High-Contrast theme mode with 2dp solid outlines.
- Navigation uses Compose Navigation with string route constants.

## 4. Joined with the website and the shared backend (Oct 2026)
- The app moved into `app/` of the main repo (subtree merge, history kept), next to `web/` and `supabase/`.
- `RemoteBackend` implements `BackendGateway` against the shared Supabase backend (docs/CONTRACT.md) with OkHttp:
  RPCs over REST with the anon key, public tables, and Realtime broadcast over one websocket. `FakeBackend` stays as
  the simulator (used when no backend URL is configured) and for the demo panel.
- The current visit is stored on the phone (`VisitRepository`, Room `stored_visits`); Home, My Turn, the slip,
  Health Journey and the queue watcher all read it instead of the hard-coded demo visit.
- Hilt was declared but never wired (no plugin, no component); it was removed along with the unused Kotlin
  Multiplatform template (`shared/`, `iosApp/`, the template `MainActivity`). Dependencies are created in
  `SevaApplication`.
- The UI adopted the website's design language (see README "Design").

