# SevaSetu backend contract (for the Patient App)

The Patient App (offline-first PWA) and the PHC Dashboard share one Supabase backend.
All queue rules and safety rules run **inside the database**. The app sends events and
renders what the backend returns. It never calculates queue positions or makes tokens official itself.

Status: v0.1 (23 Oct 2026). Breaking changes are announced before they land.

## 1. Connect

```bash
# one-time: Docker Desktop running, then from the repo root
npx supabase start          # API on http://127.0.0.1:54321
npx supabase status         # prints ANON_KEY
```

```ts
import { createClient } from "@supabase/supabase-js";
export const supabase = createClient("http://127.0.0.1:54321", ANON_KEY);
```

On a phone on the same Wi-Fi, use the laptop's LAN IP instead of 127.0.0.1.
The app uses the **anon key only**. It cannot read tables. Everything goes through the RPCs below.

## 2. Rules the app must follow

| # | Rule |
|---|------|
| IDs | Generate a UUID (`crypto.randomUUID()`) **on the device** for every patient, visit, urgent flag, symptom check and journey event. Every RPC is idempotent on that id, so you can safely resend from the outbox. |
| Outbox order | Send urgent flags first, then Priority (yellow) registrations, then Routine, then journey events (PRD 7.4). |
| Emergency (S2) | If any warning sign is answered “yes”, **do not call `register_visit`**. Show the Emergency screen, call `record_symptom_check` with `outcome: "emergency"`, and call `raise_urgent_flag` with `source: "symptom_check"` if online. |
| Bands | The app may suggest only `green` or `yellow`. The server raises the band to `yellow` for infants, elderly and pregnant patients. Staff can change it in either direction; show `band_label` and `band_provisional` exactly as returned. |
| Offline tokens (S8) | While offline, show a device-local token such as `M-x4F2` marked “Waiting to be confirmed”. Send it as `offline_token`. The official token comes back in the response and a `token_confirmed` reason is shown to the patient. |
| Times | All times are ISO-8601 UTC. Display in Asia/Kolkata. Always show `last_updated`. |
| Demo clock | The backend's clock can be sped up for the demo. Use `get_clock()` instead of the phone clock when showing queue times. |
| Privacy (S9) | No real Aadhaar/ABHA numbers. Journey events carry only a random `session_id`, never the visit or patient id. |

## 3. Enums

| Field | Values |
|---|---|
| `language` | `ks` Kashmiri, `ur` Urdu, `doi` Dogri, `hi` Hindi, `en` English |
| `age_group` | `infant`, `child`, `adult`, `elderly` |
| `special_needs[]` | `walking`, `wheelchair`, `sight`, `hearing`, `pregnant`, `child` (`walking`/`wheelchair` → stair-free route; `hearing` → visual call) |
| `band` | `green` Routine, `yellow` Priority, `red` Emergency now (staff only) |
| visit `status` | `waiting`, `called`, `done`, `emergency` (staff sent the patient to Emergency: show the Emergency screen), `cancelled` |
| doctor `status` | `in`, `delayed`, `break`, `away`, `off` (`break`/`away`/`off` → `estimate.paused = true`) |
| `channel` | `app`, `remote`, `sms` (the app uses `app`, or `remote` for pre-visit registration) |

Demo hospitals: `H01` Chinar District Hospital (full demo, 2 floors), `H02`, `H03` (smaller).
Department codes in H01: `M` General Medicine, `P` Paediatrics, `A` Maternal Health, `O` Orthopaedics.

## 4. RPCs

Call with `supabase.rpc(name, args)`.

### `register_visit({ p })` → `MyTurn`

```json
{
  "visit_id": "uuid (device)",
  "hospital_id": "H01",
  "department_code": "M",
  "channel": "app",
  "suggested_band": "green",
  "offline_token": "M-x4F2",
  "created_at": "2026-10-23T05:12:00Z",
  "doctor_note": "Cough 5 days, fever, no breathing difficulty reported, age 62",
  "preferred_doctor_id": null,
  "patient": {
    "id": "uuid (device, reuse for the same person)",
    "first_name": "Kamla",
    "age_group": "elderly",
    "age_years": 62,
    "special_needs": ["walking"],
    "language": "ks",
    "phone": null,
    "area": "Anantnag",
    "abha_ref": null,
    "consent": { "terms": true, "movement": true }
  },
  "symptom_check": {
    "id": "uuid", "ruleset_version": "demo-1", "cluster": "respiratory",
    "answers": { "...": "..." }
  }
}
```

`created_at` is honoured only when `offline_token` is set, and is capped at 45 minutes back, so it can't be used to jump the queue.

### `get_my_turn({ p_visit_id })` → `MyTurn`

The My Turn screen and the slip. The visit id works as the patient's access key: anyone who has it can read this visit, so keep it on the device only.

```json
{
  "visit_id": "…", "token": "M-070", "offline_token": "M-x4F2", "first_name": "Kamla",
  "hospital": { "id": "H01", "name": "…", "address": "…", "lat": 33.73, "lng": 75.14 },
  "department": { "id": "H01-GM", "code": "M", "name": "General Medicine", "name_i18n": { "hi": "…" } },
  "doctor": { "id": "H01-D01", "name": "Dr. Aamir Lone", "room": "Room 3", "floor": 1,
              "room_waypoint_id": "SS:H01:F1:W103", "stair_free": true, "status": "in", "status_note": null },
  "band": "yellow", "band_label": "Priority", "band_provisional": true,
  "status": "waiting",
  "now_serving": "M-015",
  "estimate": { "people_ahead": 2, "estimate_min": 14, "window_start": "…", "window_end": "…",
                "paused": false, "consult_min": 5.8 },
  "last_reason": { "type": "move_up", "reason_code": "clinical_review",
                   "message": "Your place changed after review by hospital staff.", "at": "…" },
  "urgent_flag": { "id": "…", "received_at": "…", "seen_at": null },
  "wait_anywhere_allowed": false,
  "slip_qr": "SS1:SLIP:H01:<visit_id>",
  "last_updated": "…"
}
```

- `estimate` is `null` when the patient is not waiting (called, done, emergency).
- Alert the patient when `estimate.people_ahead <= 3` (8.6), and again when `status` becomes `called`.
- Translate `last_reason.reason_code` into the patient's language; `message` is the English fallback.
  Codes: `priority_patient`, `clinical_review`, `doctor_delayed`, `doctor_unavailable`, `wrong_department`,
  `patient_request`, `no_show`, `load_balance`, `staff_correction`, `token_confirmed`.

### `link_slip({ p_hospital_id, p_token })` → `MyTurn | null`

“I have my slip” by typed token (today only). For a QR scan, parse `SS1:SLIP:<hospital>:<visit_id>` and call `get_my_turn`.

### `raise_urgent_flag({ p })` → `{ id, received_at, seen_at, seen_by }`

```json
{ "id": "uuid", "visit_id": "uuid or null", "hospital_id": "H01 (needed if no visit)",
  "session_id": "anon session (if no visit)", "source": "patient_app",
  "message": "optional", "waypoint_id": "SS:H01:F1:W012", "raised_at": "…" }
```

Until `seen_at` is set, show “Not yet received by staff. Please tell a staff member.” offline, or “Sent to staff” online.
When `seen_at` arrives, show “Staff have seen your request” with vibration.

### `get_urgent_flag({ p_id })` → same shape. Use this to poll if realtime is unavailable.

### `record_symptom_check({ p })` → void

For outcomes that don't create a visit (`emergency`, `help_desk`). Fields: `id, hospital_id, ruleset_version, language, answers, outcome, department_code, suggested_band, cluster, area, created_at`.

### `ingest_journey_events({ p_events })` → number inserted

```json
[{ "id": "uuid", "session_id": "random per visit", "hospital_id": "H01",
   "type": "entry|scan|room|wrong_turn|exit", "waypoint_id": "SS:H01:F0:W001",
   "occurred_at": "…" }]
```

Send only with movement consent. Batch freely; duplicates are ignored.

### `get_my_journey({ p_visit_id })` → Health Journey (read-only)

```json
{ "first_name": "Kamla",
  "journey": [ { "day": "2026-08-13", "hospital": "Chinar DH", "department": "General Medicine",
                 "doctor": "Dr. Aamir Lone", "status": "red", "follow_up_on": "2026-09-03",
                 "consult_ok": false, "notes": "…" } ],
  "next_follow_up": { "on": "2026-11-23", "consult_ok": true, "state": "due" } }
```

For the patient's *My Health Journey* tile (PRD 11.1). Statuses are set only by doctors and nurses; show them with the
word and icon (`red` Emergency now, `yellow` Priority, `green` Routine), oldest first, large type with Listen.
`next_follow_up.state` is `due`, `done` (they came back) or `defaulted` (missed). Cache it for offline reading.

### `get_board({ p_hospital_id })`, `get_clock()`

Public read models. `get_clock` returns `{ now, speed, today }`.

### Public tables (read-only with the anon key)

`hospitals` (address, lat/lng, `services` like `{"lab":"up","xray":"down"}` for the pre-visit check), `departments`, `doctors` (status for “doctor in?”).

## 5. Realtime

Subscribe to public broadcast channels and **refetch `get_my_turn`** when an event arrives:

```ts
supabase.channel(`visit:${visitId}`)
  .on("broadcast", { event: "visit_changed" }, refetch)
  .on("broadcast", { event: "flag_seen" }, refetch)
  .subscribe();
supabase.channel(`hospital:${hospitalId}`)
  .on("broadcast", { event: "queue_changed" }, refetch)   // someone else's change moved you
  .subscribe();
// a flag raised without a visit:
supabase.channel(`flag:${flagId}`).on("broadcast", { event: "flag_seen" }, onSeen).subscribe();
```

Payloads contain only ids. Always refetch rather than trusting the payload.

## 6. QR formats

| Use | Payload |
|---|---|
| Wall waypoint sticker | `SS:<hospital>:F<floor>:W<id>`, e.g. `SS:H01:F1:W103` (PRD 9.1) |
| OPD slip | `SS1:SLIP:<hospital>:<visit_id>` |

## 7. The hospital map (indoor navigation)

**Files:** [`web/public/graph/H01.json`](../web/public/graph/H01.json), `H02.json`, `H03.json` (one per demo hospital),
also served by the website at `/graph/<hospital>.json`. The app bundles identical copies in
`app/androidApp/src/main/assets/graph/` for offline use. Generated by `web/scripts/build_h0N_graph.py` with the shared
pieces in `graph_common.py` (run from `web/`); each build writes both copies and checks every node is reachable.

| Part | Shape |
|---|---|
| `floors[]` | `{ floor, name, name_i18n, width, height }`, plan pixels, 10 px = 1 m (H01 has two floors, H02 and H03 one) |
| `nodes[]` | `{ id, floor, x, y, sticker, tags[], name{en,hi,ur}, landmark{en,hi,ur} \| null }` |
| `edges[]` | `{ from, to, distance_m, stairs, lift, ramp, crowd_prone }`, walkable **both ways** |
| `areas[]` | `{ id, floor, label, label_i18n{en,hi,ur}, kind, x, y, w, h, capacity, waypoints[] }`, rooms and corridors to draw |
| `entrance`, `emergency` | node ids of the main gate and Emergency |

- **`sticker: true`** nodes are printed QR stickers (31 at H01, 15 at H02, 11 at H03); their QR payload is the node `id`, e.g. `SS:H01:F1:W103`.
  `sticker: false` nodes (`…:V…`) are corridor points for routing only, with nothing on the wall: never ask the patient to scan one.
- **Tags** say what a node is: `entrance`, `exit`, `emergency`, `registration`, `waiting`, `room`, `pharmacy`, `lab`, `xray`,
  `toilet`, `help`, `lift`, `stairs`, `junction`, `corridor`.
- **Doctor rooms** match the backend: `get_my_turn().doctor.room_waypoint_id` is the destination node.
- **Names** carry `en`, `hi` and `ur` (draft, awaiting native-speaker review). Show `ur` for Kashmiri and `hi` for Dogri until
  `ks` and `doi` keys are added next to them.

**Routing** (PRD 9.3): Dijkstra, cost = `distance_m` + 10 for a lift ride + 3 on `crowd_prone` edges. With *Avoid stairs*,
drop edges where `stairs` is true. Emergency routing uses `distance_m` alone. The website's implementation is
[`web/src/lib/graph.ts`](../web/src/lib/graph.ts) (`route`, `stickersOn`, `neighbours`): copy it so both sides agree.
Compass heading for "turn left/right" comes from the node coordinates (x grows east, y grows south).

**Scans → `ingest_journey_events`** (only with movement consent, under a random `session_id` per visit):

| Moment | `type` |
|---|---|
| First scan at the gate | `entry` |
| Each sticker on the route | `scan` |
| A sticker that is not the planned next one and the remaining distance went up | `wrong_turn` (then re-route from it) |
| The destination sticker | `room` |
| The gate on the way out | `exit` |

The dashboard's **Patient flow** screen reads these: who is where in the last 15 minutes, the median from `entry` to `room`,
and wrong-turn hotspots. A visitor counts as "here" at their latest scan until they scan `exit`.

## 8. Still to agree

- **SMS / missed-call fallback** (PRD 7.2 step 3): not built on either side.

Agreed: the symptom-check rules live in the app (`app/androidApp/src/main/assets/symptom_rules_demo-1.json`, version
`demo-1`, reviewed by a clinician before real use), and the `cluster` values are `fever`, `respiratory`, `diarrhoea`,
`skin`, `injury` (or none).
