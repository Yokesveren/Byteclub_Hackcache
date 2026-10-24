# Building the SevaSetu patient app

This is the guide for whoever builds the **patient app** (the offline-first PWA in the PRD, section 8).
The backend, the staff dashboard and the indoor map already exist in this repo. Your app talks to the
same backend, so everything you do shows up live on the dashboard, and everything staff do reaches the patient.

- **Exact request and response shapes:** [`docs/CONTRACT.md`](docs/CONTRACT.md). This guide explains *how* to use it.
- **Product spec:** `SevaSetu_PRD_v1_0_Complete.docx`, especially sections 4 (safety rules), 7 (offline), 8 (screens) and 9 (navigation).

---

## Contents

1. [What you are building, and what already exists](#1-what-you-are-building-and-what-already-exists)
2. [Repo layout and how we work together](#2-repo-layout-and-how-we-work-together)
3. [Run the backend on your machine](#3-run-the-backend-on-your-machine)
4. [Connect your app (laptop and real phone)](#4-connect-your-app-laptop-and-real-phone)
5. [The five rules of this backend](#5-the-five-rules-of-this-backend)
6. [Screen by screen: what to call](#6-screen-by-screen-what-to-call)
7. [Indoor navigation with the map file](#7-indoor-navigation-with-the-map-file)
8. [Offline: the outbox](#8-offline-the-outbox)
9. [Live updates](#9-live-updates)
10. [Test your app against the dashboard](#10-test-your-app-against-the-dashboard)
11. [Safety checklist (must pass before the demo)](#11-safety-checklist-must-pass-before-the-demo)
12. [Gotchas](#12-gotchas)
13. [Still open between us](#13-still-open-between-us)

---

## 1. What you are building, and what already exists

**Your app (PRD P0 unless marked):**

| Screen | PRD | Backend you use |
|---|---|---|
| Language choice, first-launch download, consent | 8.1 | public tables + map file |
| Emergency button on **every** screen, "I need urgent help" | 8.4 | `raise_urgent_flag`, `get_urgent_flag` |
| Registration, one question per screen | 8.2 | `register_visit` |
| Guided symptom check (offline, rules on the phone) | 8.3 | `register_visit` / `record_symptom_check` |
| OPD slip with QR | 8.5 | `register_visit` / `get_my_turn` |
| My Turn (live queue, alert at 3 ahead, called) | 8.6 | `get_my_turn` + realtime |
| I have my slip (type token or scan slip QR) | 8.7 | `link_slip` / `get_my_turn` |
| Find a place: QR-sticker navigation, avoid stairs, I'm lost | 9 | `web/public/graph/H01.json`, `ingest_journey_events` |
| Settings (language, text size, contrast, read aloud, stairs, alerts, consent) | 8.8 | local only |
| My Health Journey (P1) | 11.1 | `get_my_journey` |
| Offline everything above, with honest "last updated" | 7.2–7.3 | your outbox (section 8 here) |

**Already done for you:**
- **Backend** (`supabase/`): queue rules, token allocation, wait windows, urgent flags, staff actions, transparency log, follow-ups, Health Journey, flow analytics. Every safety rule is enforced server-side, so a bug in the app cannot, for example, queue an emergency.
- **Staff website** (`web/`): facility queues with the urgent alarm, counter, waiting-room board, patient-flow crowd map, doctor desk, district view, demo controls. Use it to see your app working.
- **Indoor map** (`web/public/graph/H01.json`) and a **route planner** (`web/src/lib/graph.ts`) you can copy.
- **Demo data:** three fictional hospitals, 15 doctors, a busy 10:30 AM OPD, returning patients with history.

---

## 2. Repo layout and how we work together

```
hackcache/
├── APP_README.md        ← you are here
├── README.md            project overview, website
├── docs/CONTRACT.md     the API contract: every call, every field
├── supabase/            backend: migrations (schema + rules), seed.sql (demo data)
├── web/                 staff website (Next.js). Also hosts the map file:
│   ├── public/graph/H01.json      indoor map (bundle a copy in your app)
│   ├── scripts/build_h01_graph.py edit the map here, then regenerate
│   └── src/lib/graph.ts           Dijkstra route planner: copy it
└── app/                 ← put the patient app here (suggested)
```

- **Keep to your folder.** The website team owns `web/` and `supabase/`. If you need a backend change (a new field, a new function), ask, or open a PR that touches only `supabase/` and update `docs/CONTRACT.md` in the same PR.
- **Branches:** work on a branch (`app/<feature>`) and merge to `main` with a PR, so we never overwrite each other.
- **Never commit** `.env` files, real names, real phone numbers or real Aadhaar/ABHA numbers. All data is made up (rule S9).

---

## 3. Run the backend on your machine

You need **Docker Desktop** (running) and **Node 20+**.

```bash
git clone https://github.com/Yokesveren/Byteclub_Hackcache.git
cd Byteclub_Hackcache

npx supabase start          # first run downloads images (a few minutes), then applies migrations + seed
npx supabase status         # prints API URL and ANON_KEY (and Studio URL)
```

| Thing | Local address |
|---|---|
| API (what your app calls) | `http://127.0.0.1:54321` |
| Studio (browse tables, run SQL) | `http://127.0.0.1:54323` |
| Postgres | `postgresql://postgres:postgres@127.0.0.1:54322/postgres` |

**Reset everything** to the 10:30 AM demo morning: `npx supabase db reset`.
If live updates stop working after a reset: `docker restart supabase_realtime_sevasetu`.

**Optional but very useful: run the staff website** to watch your app's effects.

```bash
cd web
cp .env.example .env.local   # paste ANON_KEY from `npx supabase status`
npm install
npm run dev                  # http://localhost:3000, sign-in buttons on /login
```

---

## 4. Connect your app (laptop and real phone)

The app uses **only the public anon key**. It can never read tables directly; everything goes through functions.

```ts
import { createClient } from "@supabase/supabase-js";

export const supabase = createClient(
  import.meta.env.VITE_SUPABASE_URL,      // http://127.0.0.1:54321 on the laptop
  import.meta.env.VITE_SUPABASE_ANON_KEY, // ANON_KEY from `npx supabase status`
);
```

**Testing on a real phone (same Wi-Fi):**
1. Find the laptop's LAN IP (`ipconfig` on Windows, e.g. `192.168.1.23`).
2. Use `http://192.168.1.23:54321` as the Supabase URL. The local stack already listens on all interfaces.
3. Serve your app on the LAN too (e.g. `vite --host`). If the phone cannot connect, allow ports 54321 and your dev port through Windows Firewall.
4. **Camera access (QR scanning) needs HTTPS or `localhost`.** On a phone over plain HTTP the camera is blocked, so use an HTTPS tunnel or your dev server's HTTPS option for scanner testing.

The local keys are public demo keys. For the hackathon demo we will move to a hosted Supabase project and share a new URL and key.

---

## 5. The five rules of this backend

1. **Make IDs on the phone.** Every patient, visit, urgent flag, symptom check and scan gets a UUID from `crypto.randomUUID()` before it is sent. All calls are **idempotent**: sending the same ID twice is safe, which is what makes an offline retry queue work.
2. **The server decides the queue.** You send a registration; the server picks the doctor, the token (`M-017`), the position and the time window. You *show* what comes back and never compute positions yourself.
3. **`visit_id` is the patient's key.** Whoever has it can read that visit. Store it on the phone only (never in a URL you share, never in logs).
4. **Emergencies are never queued.** If the symptom check finds a warning sign, you do **not** call `register_visit` (the server would refuse a red band anyway). You show the Emergency screen.
5. **Times are UTC ISO strings; show them in Asia/Kolkata.** The demo has a speed-up clock: use `get_clock()` instead of the phone's clock when you show queue times.

---

## 6. Screen by screen: what to call

Shapes are in `docs/CONTRACT.md` section 4. Enumerations (languages, special needs, statuses) are in section 3.

### First launch (8.1)
- **Language cards:** `ks` Kashmiri, `ur` Urdu (right to left), `doi` Dogri, `hi` Hindi, `en` English. Bundle fonts for Perso-Arabic and Devanagari.
- **One-time download for offline use:**
  - `supabase.from("hospitals").select("*")`, `departments`, `doctors`: public, read-only (addresses, coordinates, service status, OPD hours).
  - The map: `web/public/graph/H01.json` (bundle it in the app build).
  - Your own symptom-check rules, FAQ cards and recorded audio.
- **Consent:** store `{ terms: true, movement: true|false }`; send it inside `patient.consent`. Without movement consent you must not send scan events (S9), but navigation still works.

### Emergency button, on every screen (8.4, S1)
Works with **no network**: the route to Emergency (the map's `emergency` node), 112 and 108 are bundled. "I need urgent help":

```ts
const flag = { id: crypto.randomUUID(), visit_id, hospital_id: "H01", source: "patient_app",
               message: "Feeling worse", waypoint_id: lastScan, raised_at: new Date().toISOString() };
await supabase.rpc("raise_urgent_flag", { p: flag }); // or queue it first, see section 8
```

- **Before staff see it** (`seen_at` null): show *"Not yet received by staff. Please tell a staff member."* when offline, or *"Sent to staff"* when online.
- **When staff tap Seen** on the dashboard, `seen_at` arrives (realtime `flag_seen`, or poll `get_urgent_flag`): show *"Staff have seen your request"* with vibration.
- **No visit yet?** Send `hospital_id` and a `session_id` instead of `visit_id`, and listen on `flag:<id>`.

### Registration, one question per screen (8.2)
Map the seven steps to the `register_visit` payload:

| Step | Field |
|---|---|
| Who is this for? | your UI (assisted mode); the patient's details go in `patient` |
| ABHA? | `patient.abha_ref` (mocked, made-up only) |
| Name | `patient.first_name` (first name is enough) |
| Age | `patient.age_group`: `infant` / `child` / `adult` / `elderly`, plus optional `age_years` |
| Special needs | `patient.special_needs`: `walking`, `wheelchair`, `sight`, `hearing`, `pregnant`, `child` |
| Area | `patient.area` (district name, optional) |
| Phone | `patient.phone` (optional, digits) |

Save every step locally (save-and-resume). Reuse the same `patient.id` for the same person next time.
- `walking` / `wheelchair` → the server marks the visit stair-free; the app should default *Avoid stairs* on.
- `hearing` → the visit gets a visual call on the board; the app should flash and vibrate on alerts.

### Symptom check (8.3), runs fully offline
1. **Warning signs first.** Any "yes" → Emergency screen, no token. Record it (anonymous) when you can:
   `record_symptom_check({ p: { id, hospital_id, ruleset_version, language, answers, outcome: "emergency", cluster, area } })`
   and raise a flag with `source: "symptom_check"` if online.
2. **Otherwise** → a department (`department_code`: `M` General Medicine, `P` Paediatrics, `A` Maternal Health,
   `O` Orthopaedics at H01) and a suggested band `green` (Routine) or `yellow` (Priority), plus a 2–4 line
   `doctor_note` ("Cough 5 days, fever, age 62"). Never a diagnosis.
3. Send it all with the registration:

```ts
const myTurn = await supabase.rpc("register_visit", { p: {
  visit_id: crypto.randomUUID(),
  hospital_id: "H01",
  department_code: "M",
  channel: "app",
  suggested_band: "green",
  doctor_note: "Cough 5 days, fever, no breathing difficulty reported, age 62",
  patient: { id: patientId, first_name: "Kamla", age_group: "elderly", age_years: 62,
             special_needs: ["walking"], language: "ks", area: "Anantnag",
             consent: { terms: true, movement: true } },
  symptom_check: { id: crypto.randomUUID(), ruleset_version: "demo-1",
                   cluster: "respiratory", answers: { /* ... */ } },
}});
```

The server may raise a band to Priority (infant, elderly, pregnant); it never lowers one (S3).
`cluster` feeds the district outbreak view: use `fever`, `respiratory`, `diarrhoea`, `skin` or `injury`.

### OPD slip (8.5)
Everything comes back in `MyTurn`: `token`, `first_name`, `department`, `doctor.room` / `floor` / `stair_free`,
`estimate.people_ahead`, `estimate.window_start` / `window_end`, `band_label` with `band_provisional`, and `slip_qr`.
- Show `band_label` with its icon and word, and "(provisional)" until staff confirm it.
- Show the window as a range: *"Likely between 11:20 and 11:50 · Last updated at 10:42"*.
- **QR:** encode `slip_qr` exactly (`SS1:SLIP:H01:<visit_id>`). It carries no health data (S9).
- **"Open in Google Maps":** `https://www.google.com/maps/dir/?api=1&destination=<lat>,<lng>` from `hospital`.

### My Turn (8.6)
- `get_my_turn({ p_visit_id })` on open, then refresh on realtime events (section 9).
- **Alerts:**
  - When `estimate.people_ahead <= 3`, alert with sound, vibration and a full-screen card.
  - When `status` becomes `called`, alert again with the room and **Take me there**.
- `estimate.paused === true` → the doctor is on break or away; say so and hide the window.
- `last_reason` → show one line when the patient's place changes. Translate `reason_code` (list in the contract);
  `message` is the English fallback.
- `status === "emergency"` → staff sent them to Emergency: show the Emergency screen now.
- Every queue screen carries *"Feeling worse? Press Urgent help"* (S4).

### I have my slip (8.7)
- **Typed token:** `link_slip({ p_hospital_id: "H01", p_token: "M-017" })` (today only).
- **Scanned slip:** parse `SS1:SLIP:<hospital>:<visit_id>` and call `get_my_turn`.

### My Health Journey (11.1, P1)
`get_my_journey({ p_visit_id })` → past visits with staff-set status (`red` Emergency now, `yellow` Priority,
`green` Routine), notes and the next follow-up. Read-only, large type, with Listen. Cache it for offline.

---

## 7. Indoor navigation with the map file

**File:** `web/public/graph/H01.json`. Full format in `docs/CONTRACT.md` section 7. In short:

- `floors[]`: two floors, 1000 × 560 plan pixels each (10 px = 1 m).
- `nodes[]`: `sticker: true` nodes are printed QR stickers (31). Their QR payload **is** the node id, e.g. `SS:H01:F1:W103`.
  `sticker: false` (`…:V…`) are corridor points for routing only: never ask the patient to scan one.
- `edges[]`: walkable both ways; `stairs`, `lift`, `crowd_prone` flags.
- `areas[]`: rooms and corridors to draw the floor plan.
- `entrance` and `emergency`: node ids of the gate and Casualty.
- **Doctor rooms:** `MyTurn.doctor.room_waypoint_id` is the destination node.

**Route planner:** copy `web/src/lib/graph.ts` (plain TypeScript, no dependencies):

```ts
import graph from "./H01.json";
import { route, stickersOn } from "./graph";

const path = route(graph, lastScannedSticker, myTurn.doctor.room_waypoint_id, { avoidStairs: true });
const toScan = stickersOn(graph, path!); // the stickers the patient will pass, in order
```

- **Costs (PRD 9.3):** distance in metres, +10 for a lift ride, +3 on crowd-prone corridors.
- **Avoid stairs** removes stair edges; `null` means no stair-free route exists, so offer to call a staff member.
- **Emergency routing** (`{ emergency: true }`) is plain shortest distance.

**Guidance:**
- **One step at a time.** Use the node's `landmark.en` ("Next to the green notice board") and work out left or right from the coordinates (x grows east, y grows south). End each step with "Scan the sticker at the end of this corridor".
- **Wrong turn (9.5):** a scanned sticker that is not the planned next one, and the remaining distance went up → say *"No problem. Let's take a new route."*, re-route from it, and send a `wrong_turn` event.
- **No scan for about 90 seconds** on an active route → *"Are you lost?"* with **Scan nearest sticker** and **Call a helper**.

**Scan events** (only with movement consent, under a random `session_id` per visit, never the visit or patient id):

| Moment | `type` |
|---|---|
| First scan at the gate | `entry` |
| Each sticker on the way | `scan` |
| Off-route sticker | `wrong_turn` |
| Destination sticker | `room` |
| Gate on the way out | `exit` |

```ts
await supabase.rpc("ingest_journey_events", { p_events: [
  { id: crypto.randomUUID(), session_id, hospital_id: "H01", type: "scan",
    waypoint_id: "SS:H01:F0:W006", occurred_at: new Date().toISOString() },
]});
```

They appear live on the dashboard's **Patient flow** screen (crowd map, wrong-turn hotspots).
Names are English only in the map for now; add `hi`, `ur`, `ks`, `doi` next to `en` as translations arrive.

---

## 8. Offline: the outbox

The PRD's ladder (7.2): full app online → **app offline** → SMS → paper slip. Your job is the first two.

- **Keep an outbox** in IndexedDB of calls not yet sent: `{ fn, args, priority, created_at }`. It must survive the app being closed.
- **Flush order** when the signal returns (7.4): urgent flags first, then Priority registrations, then Routine, then scan events.
- **Retry freely.** Every call is idempotent on its client ID.
- **Offline registration:**
  - Show a device-local token such as `M-x4F2` marked **"Waiting to be confirmed"** (S8).
  - Send it as `offline_token` with the original `created_at`.
  - The server issues the official token, and `last_reason` explains the change.
  - The server honours `created_at` for queue order only when `offline_token` is set, and at most 45 minutes back.
- **Cache the last `MyTurn`** with its `last_updated`, and show *"Queue position may be out of date. Last updated at 10:42."* (S8).
- **Available offline:** Emergency screen, map, routes, Listen, symptom check, slip, last-known queue, Health Journey (read-only).
- **Online only:** chatbot, ABHA lookup, live queue.

---

## 9. Live updates

The database broadcasts on **public** channels. Payloads contain only ids: **always refetch** `get_my_turn`, never trust the payload.

```ts
const ch = supabase.channel(`visit:${visitId}`)
  .on("broadcast", { event: "visit_changed" }, refetch) // called, sent to Emergency, moved, re-tokened
  .on("broadcast", { event: "flag_seen" }, refetch)     // staff saw the urgent request
  .subscribe();

const hosp = supabase.channel(`hospital:${hospitalId}`)
  .on("broadcast", { event: "queue_changed" }, refetch) // someone else's change moved this patient
  .subscribe();
```

Also poll `get_my_turn` every 20–30 s as a safety net (networks drop). Unsubscribe with `supabase.removeChannel(ch)`.

---

## 10. Test your app against the dashboard

Run the website (section 3) and open `http://localhost:3000/login`. One tap signs in (password for all: `sevasetu-demo`).

| To check | Do this |
|---|---|
| Your registration lands | Register in the app → **Triage nurse** → Facility: your token appears in the doctor's column. Also `/board/H01` (public waiting-room board) |
| "Called" reaches the phone | Facility → **Call next** on your doctor until your token is called. Your My Turn should flip to "called" within a second |
| Band confirmation | Facility → click your row → **Confirm Priority** or **Change to Routine** → the app shows the new band and the reason |
| Urgent flag | Press "I need urgent help" → Facility shows a red banner with an alarm → **Seen** → the app shows "Staff have seen your request" |
| Sent to Emergency | Facility → your row → **Send to Emergency** → the app must switch to the Emergency screen |
| Doctor status | **Doctor** login → My patients → **On break** → your time window shows as paused |
| Scan events | Scan stickers → **Demo operator** → Patient flow: your visitor appears on the map |
| Health Journey | **Doctor** login → record a status and follow-up for your patient → `get_my_journey` shows it |
| Typed token | Counter → register someone → type their token in "I have my slip" |

**Printable stickers for testing:** generate QR codes from node ids in `H01.json` (any QR generator), for example
`SS:H01:F0:W001` (gate), `SS:H01:F0:W002`, `SS:H01:F0:W006`, `SS:H01:F0:W016` (lift), `SS:H01:F1:W016`, `SS:H01:F1:W110`, `SS:H01:F1:W103` (Room 3).
Stick them around a room and walk the route.

**Demo controls** (Demo operator login) can speed up the clock, add patients, trigger a flag and reset everything.

---

## 11. Safety checklist (must pass before the demo)

From PRD section 4 and 17.3. The backend enforces what it can; these are the app's part.

- [ ] **S1:** the Emergency button is on every screen, including language choice, and opens in under 1 s **in airplane mode**, with the route, 112 and 108.
- [ ] **S2:** a "yes" on any warning sign ends the flow with *"Go to Emergency now. You don't need a token."* No registration call is made.
- [ ] **S3:** the app never shows a lower band than the server returned.
- [ ] **S4:** every queue screen says *"Feeling worse? Press Urgent help"*. No screen says the patient can wait.
- [ ] **S5:** the urgent flag shows its honest delivery state, and "Staff have seen your request" arrives.
- [ ] **S6:** no Wait Anywhere or remote token for flagged or Priority patients (`MyTurn.wait_anywhere_allowed`).
- [ ] **S7:** the chatbot (if built) answers only approved FAQ content and hands off to Emergency on a warning sign.
- [ ] **S8:** offline screens show "Last updated at …", and offline tokens say "Waiting to be confirmed".
- [ ] **S9:** the slip shows token, first name and department only; scans carry only a random session id; no real identities anywhere.
- [ ] **S10:** when the place in the queue changes, the patient sees the reason (`last_reason`).
- [ ] Every colour-coded status also has a word and an icon (never colour alone).

---

## 12. Gotchas

- **`register_visit` rejects `suggested_band: "red"`.** That is deliberate (S2); route the patient to Emergency instead.
- **Typed tokens work for today only** (`link_slip`); the QR path works any time while the visit exists.
- **The demo clock** can be sped up from Demo controls. If your times look wrong, check `get_clock()` and ask whoever is running the demo.
- **After `npx supabase db reset`,** all visits are recreated with new ids: clear the app's stored `visit_id`s.
- **Realtime silent after a reset?** `docker restart supabase_realtime_sevasetu`.
- **H02 and H03 have no indoor map yet.** Navigation is H01 only. The rest (queue, slip, My Turn) works at all three.
- **Translations** in the database are a start (`hi`, `ur` for department names) and need native-speaker review before any real use.
- **Errors come back as messages** (`error.message`), e.g. "unknown department for hospital H01". Show a friendly line and keep the outbox entry for retry only if it was a network error, not a validation error.

---

## 13. Still open between us

| Topic | Status |
|---|---|
| Symptom-check rule set (JSON on the phone) | **You own it.** Share the `cluster` values you emit (currently `fever`, `respiratory`, `diarrhoea`, `skin`, `injury`) so the outbox and the district view agree |
| SMS / missed-call mock screen (PRD 7.2 step 3) | Not built. Decide who does it |
| Map translations (`hi`, `ur`, `ks`, `doi` names and landmarks) | English only in `H01.json` |
| Hosted backend for the demo | Website team will set it up and share the URL and anon key |
| Chatbot (P1, online only) | Not started on either side |

Questions about the backend, a missing field, or anything in this guide: ask before working around it.
Changing the contract is easy; undoing a workaround is not.
