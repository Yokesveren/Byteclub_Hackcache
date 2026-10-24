# FEATURES.md: status

What works today, checked against the code and the live backend (October 2026). "Partial" says what is missing.

## Done

- **Shell and design:** website palette and type, Emergency bar on every screen, four text sizes, high contrast,
  loading, empty, error and offline states.
- **Settings saved on the phone:** language, onboarding, consent, text size, contrast, avoid stairs, alerts.
- **Languages:** Kashmiri, Urdu, Dogri, Hindi, English; screens, directions, place names and alerts in Hindi and Urdu
  (draft), right-to-left layout for Urdu and Kashmiri, Listen on every main screen.
- **Live backend:** every patient RPC, public tables and Realtime broadcast through `RemoteBackend`; the simulator
  remains for demos without a backend.
- **Outbox:** priority order, idempotent retries, retries items left mid-send when the app closed, refused requests
  shown instead of retried.
- **Registration:** hospital choice, real departments per hospital, special needs, age, optional phone and area, the
  same patient id for "myself" across visits.
- **Symptom check:** 11 warning signs from `symptom_rules_demo-1.json`; any yes opens Emergency, records the outcome and
  raises an urgent flag; otherwise department, suggested band, cluster and doctor note; "not sure" goes to the help desk.
- **Slip, My Turn, Called:** live updates, time windows in local time, reasons for changes, doctor-away notice,
  share, spoken call, flash for hearing needs.
- **Background alerts:** at 3 ahead, when called, when sent to Emergency.
- **I have my slip:** slip QR or typed token.
- **QR scanning:** CameraX + ML Kit for wall stickers and slips, camera permission flow, pick-a-place fallback.
- **Indoor navigation:** maps for all three demo hospitals; destination picker; step-by-step directions with turns,
  distances and landmarks; floor plan; stair-free routing; wrong-turn rerouting; "are you lost?"; anonymous scan
  events with consent.
- **Emergency:** route to Casualty from the last scan, 112 and 108, honest urgent-flag state with live "seen".
- **Health journey:** past visits and next follow-up, cached offline.
- **Before you visit:** live doctor status and services per hospital.
- **Offline readiness:** real checks and the outbox queue with "send now".

## Partial

- **Translations:** Hindi and Urdu are drafts by a non-native writer; Kashmiri and Dogri use Urdu and Hindi. Needs
  native-speaker review (`TRANSLATION_REVIEW.md`). Doctor notes stay in English for the doctor.
- **Listen:** uses the phone's text-to-speech; Kashmiri and Dogri voices are rarely installed, so they fall back to
  Urdu and Hindi voices. No recorded audio yet.
- **Delivery:** the APK is a debug build served from the website's `/patient-app` page. A hosted backend (https) is
  needed for phones away from the laptop.

## Not built

- SMS / missed-call fallback for phones without data (PRD 7.2 step 3); `ui/sms` is a simulator screen only.
- Chatbot (P1, online only).
- ABHA lookup (mocked field only).
