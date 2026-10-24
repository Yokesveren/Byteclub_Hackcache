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
