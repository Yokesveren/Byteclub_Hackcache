# SevaSetu

Offline-first smart queue, accessible navigation and continuity of care for government hospitals in Jammu and Kashmir.
See `SevaSetu_PRD_v1_0_Complete.docx`.

| Part | Where | Owner |
|---|---|---|
| Backend (Postgres rules, RPCs, realtime) | `supabase/` | website team |
| PHC Dashboard (web) | `web/` | website team |
| Patient App (Android, Jetpack Compose) | `app/` | app team: run it with [`app/README.md`](app/README.md); guide for building on it: [`APP_README.md`](APP_README.md); contract: [`docs/CONTRACT.md`](docs/CONTRACT.md) |

All hospitals, doctors and patients are made up.

## Run locally

Needs Docker Desktop and Node 20+.

```bash
npx supabase start              # first run downloads images; applies migrations + seed
cd web
cp .env.example .env.local      # paste ANON_KEY from `npx supabase status`
npm install
npm run dev                     # http://localhost:3000
```

Sign in with a demo account (password `sevasetu-demo`, buttons on the login page):
`triage@`, `counter@`, `doctor@`, `admin@`, `demo@sevasetu.demo`.

| Screen | Path |
|---|---|
| District overview: impact, all hospitals, load balancing, forecast, outbreak signal (district officer) | `/district` |
| Facility view: urgent flags, queues, staff actions (triage) | `/facility` |
| My patients: patient with you, Health Journey, record status and follow-up (doctor) | `/doctor` |
| Follow-ups: missed and due follow-ups, call / SMS / community actions (triage) | `/followups` |
| Registration counter + printable QR slip | `/counter` |
| Waiting-room board (public, no login) | `/board/H01` |
| Patient flow: crowd map from QR scans, wrong-turn hotspots, visitor simulator | `/flow` |
| Transparency log | `/log` |
| Demo controls: clock speed, autopilot, add patients, urgent flag, reset | `/demo` |
| Get the patient app: what it does, APK download and a QR to scan (public) | `/patient-app` |

`npx supabase db reset` rebuilds the database and the 10:30 AM demo scenario. If live updates stall after a reset,
run `docker restart supabase_realtime_sevasetu`.

## The patient app

The Android app in `app/` uses the same backend: a patient registering on the phone appears in the Facility view,
staff actions reach the phone at once, scans feed Patient flow. To try it on a phone over USB:

```bash
cd app
./gradlew :androidApp:installDebug          # after creating app/local.properties, see app/README.md
adb reverse tcp:54321 tcp:54321             # the phone reaches the laptop's backend through the cable
```

## The hospital maps

`web/public/graph/H01.json`, `H02.json` and `H03.json` are the indoor maps of the three demo hospitals, shared with
the patient app (QR waypoints, corridors, lift, stairs, areas with capacities, names in English, Hindi and Urdu).
Edit `web/scripts/build_h0N_graph.py` (shared pieces in `graph_common.py`) and run it from `web/`: it writes both the
website's and the app's copy and checks every place is reachable. Routing lives in `web/src/lib/graph.ts` (and its
Kotlin twin in the app). See `docs/CONTRACT.md` section 7.

## Where the rules live

- `supabase/migrations/*_core.sql`: schema, wait-window formula (PRD 13.1), allocation (10.3), ordering and
  fairness guard (10.4), patient RPCs, staff actions with mandatory reasons (S10), realtime broadcasts, RLS.
- `*_demo.sql`: demo clock, seed generator, autopilot.
- `*_dashboard.sql`: dashboard read models.
- `*_flow.sql`: patient flow from anonymous scans (`get_flow`) and its live broadcast.
- `*_roles.sql`: Health Journey, follow-ups, the doctor's desk, the district overview (forecast, outbreak, impact),
  six weeks of synthetic history, and returning patients for the demo (`demo_reset_all`).
