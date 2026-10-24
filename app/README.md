# SevaSetu patient app (Android)

The patient side of SevaSetu: an OPD token without the counter queue, your place in the queue as it moves, and the
way to the doctor's room by scanning QR stickers on the walls. It talks to the same backend as the staff website
(`../supabase`, contract in `../docs/CONTRACT.md`), so whatever the patient does shows up on the dashboard and
whatever staff do reaches the phone. Everything that matters in an emergency works with no signal.

Kotlin, Jetpack Compose (Material 3), Room, WorkManager, CameraX + ML Kit, OkHttp. Min SDK 26, target 37.

---

## Run it

You need the backend running (from the repo root, Docker on): `npx supabase start`, then `npx supabase status`
for the `ANON_KEY`.

1. Create `app/local.properties` (git-ignored):
   ```properties
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   sevasetu.supabaseUrl=http://127.0.0.1:54321
   sevasetu.supabaseAnonKey=<ANON_KEY>
   ```
2. Build and install: `./gradlew :androidApp:installDebug` (JDK 21; Gradle downloads one if needed).
3. Let the phone reach the laptop's backend:
   - **USB (works on any network):** `adb reverse tcp:54321 tcp:54321` and keep the URL `http://127.0.0.1:54321`.
     Run it again after replugging.
   - **Wi-Fi:** use `http://<laptop LAN IP>:54321`. Many campus and office networks block phone-to-laptop traffic;
     if the app says offline, use USB.
   - **Emulator:** `http://10.0.2.2:54321`.
4. Home shows "Connected to the hospital". With both settings left empty the app runs on its built-in simulator
   ("Simulator mode"), which needs no backend at all.

Debug builds allow plain `http` for the local backend; release builds are https-only, so a hosted backend needs an
`https://` URL.

**Publish the APK on the website** (the website's "Get the app" page, `/patient-app`, serves it):
`cp androidApp/build/outputs/apk/debug/androidApp-debug.apk ../web/public/download/sevasetu-patient.apk`
(git-ignored; build it with the URL the downloading phones can reach).

## Tests

```bash
./gradlew :androidApp:testDebugUnitTest
```
- `GraphRouterTest`, `DirectionsTest`: routes and spoken directions on all three hospital maps, stair-free routes,
  turns worked out from the plan, Hindi directions.
- `I18nTest`: language fallbacks (Kashmiri to Urdu, Dogri to Hindi) and that every translation keeps its placeholders.
- `RemoteBackendLiveTest`: the real client against a running backend; skipped unless configured. It creates fictional
  visits, so use a local or demo backend only:
  ```powershell
  $env:SEVASETU_TEST_URL='http://127.0.0.1:54321'; $env:SEVASETU_TEST_ANON_KEY='<ANON_KEY>'; ./gradlew :androidApp:testDebugUnitTest
  ```

---

## What the patient can do

| Screen | What happens | Backend |
|---|---|---|
| Language, consent | Five languages in their own scripts; consent in plain words, scan sharing optional. Asked once. | none |
| Home | The ticket (token, people ahead, time window), or "register now"; connection status. | `get_my_turn` |
| Register | Who, which hospital, first name (phone and area optional), age, help needed. | `register_visit` |
| Symptom check | 11 warning signs first: any yes opens Emergency, raises an urgent flag, no token (S2). Then the main problem and how long, giving a department, a suggested band and a note for the doctor. | `record_symptom_check`, `raise_urgent_flag` |
| OPD slip | Token, name, department, room, time window, QR (`SS1:SLIP:<hospital>:<visit>`), share, directions to the gate. | |
| My turn | The big number of people ahead, the time window, why the place changed (S10), doctor away notice. Live through Realtime. | `get_my_turn`, `visit:` / `hospital:` channels |
| Alerts | A notification watches the queue in the background: at 3 ahead, when called, when sent to Emergency. | same |
| Called | The room in the largest type, spoken and vibrated; screen flash for hearing needs. | |
| I have my slip | Scan the slip QR or type the token (normalised, e.g. `m17` = `M-017`). | `link_slip`, `get_my_turn` |
| Find your way | Pick a destination (your doctor first); scan stickers; one step at a time with turns, distances and landmarks; floor plan; stair-free routes; "no problem, a new route" after a wrong turn; "are you lost?" after 90 s. | `ingest_journey_events` (only with consent) |
| Emergency | On every screen. The way to Casualty from where you last scanned, 112 and 108, and "I need urgent help" with its honest state: not yet received, sent, seen by staff (S5). | `raise_urgent_flag`, `get_urgent_flag`, `flag:` channel |
| Health journey | Past visits with the status staff recorded and the next follow-up; kept for offline. | `get_my_journey` |
| Before you visit | Is the doctor in, is the X-ray working, directions to the gate, per hospital. | public tables |
| Settings | Language, text size (4 steps), high contrast, avoid stairs, sound, vibration, flash, scan sharing, forget this visit. | |
| Ready offline? | What is really stored on this phone, and what is waiting to send (with "send now"). | |

### Offline (PRD 7)

- The hospital list, the three indoor maps, the symptom rules, the Emergency route and the last ticket are on the phone.
- Registering with no signal gives a phone-made token (`M-x4F`) marked "Waiting to be confirmed"; the outbox sends it
  with its original time when signal returns and the official token replaces it.
- The outbox sends in priority order: urgent help, Priority registrations, Routine registrations, symptom records,
  scan events. Every call is idempotent on its client id, so retries are safe. A request the hospital refuses (a rule
  or validation error) is shown, not retried forever.

### Languages

Screens, directions, place names and alerts are in English, Hindi and Urdu; Urdu and Kashmiri lay out right to left.
Kashmiri is shown in Urdu script and Dogri in Hindi until their own translations exist. Listen reads screens aloud
where the phone has a voice for the language.

- Screen text: `i18n/strings.py` (keyed by the English text in the code). After editing, run
  `python i18n/build_strings.py`; it regenerates `ui/i18n/Strings.kt` and lists any text in the code that has no
  translation yet. In code, wrap visible text in `tr("...")` (or `trNow("...")` outside a composable).
- Place names and landmarks: in the maps, built by `../web/scripts/graph_common.py`.
- All translations are first drafts: see `TRANSLATION_REVIEW.md`.

### Hospital maps

`androidApp/src/main/assets/graph/H01.json`, `H02.json`, `H03.json`, identical to the website's copies in
`../web/public/graph`. Build them with `python scripts/build_h0N_graph.py` from `web/`; each build writes both copies
and checks that every place is reachable from the gate. Wall stickers carry the node id (`SS:H01:F1:W103`); the
sticker sheet (Settings > Wall sticker sheet) shows them all for printing.

---

## Code map

```
androidApp/src/main/java/app/sevasetu/patient/
  SevaApplication.kt        wiring: backend (live or simulator), database, outbox, settings, speech, maps, directory
  MainActivity.kt, NavGraph.kt
  data/gateway/             BackendGateway, RemoteBackend (Supabase REST + Realtime), FakeBackend (simulator)
  data/outbox/              priority outbox + WorkManager retry
  data/visit/               VisitRepository (the current ticket), Directory (hospitals, departments, doctors; cached)
  data/navigation/          GraphRouter (Dijkstra, same rules as web/src/lib/graph.ts), Directions, NavSession
  data/prefs/               Settings (SharedPreferences), Speaker (text to speech)
  service/QueueWatcherService.kt   background queue alerts
  ui/                       one package per screen; ui/components (shared design pieces), ui/theme, ui/i18n
```

Design follows the website (`../web/src/app/globals.css`): navy frame `#0b2a3c`, scrub teal actions `#0b7a75`,
clinical mint `#9fe5cf`, ward blue and linen fields, Chivo with black-weight capitals, and status chips that always
carry a word and an icon. Chivo is bundled under the SIL Open Font License (`androidApp/OFL-Chivo.txt`).

See also: `DECISIONS.md` (why things are the way they are), `SAFETY_CHECKLIST.md` (S1 to S10),
`FEATURES.md` (status), `TRANSLATION_REVIEW.md`.
