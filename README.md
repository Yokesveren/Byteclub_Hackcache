# SevaSetu

Offline-first smart queue, accessible navigation and continuity of care for government hospitals in Jammu and Kashmir.
See `SevaSetu_PRD_v1_0_Complete.docx`.

| Part | Where | Owner |
|---|---|---|
| Backend (Postgres rules, RPCs, realtime) | `supabase/` | website team |
| PHC Dashboard (web) | `web/` | website team |
| Patient App (PWA) | separate | app team, codes against [`docs/CONTRACT.md`](docs/CONTRACT.md) |

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
| Facility view: urgent flags, queues, staff actions | `/facility` |
| Registration counter + printable QR slip | `/counter` |
| Waiting-room board (public, no login) | `/board/H01` |
| Transparency log | `/log` |
| Demo controls: clock speed, autopilot, add patients, urgent flag, reset | `/demo` |

`npx supabase db reset` rebuilds the database and the 10:30 AM demo scenario. If live updates stall after a reset,
run `docker restart supabase_realtime_sevasetu`.

## Where the rules live

- `supabase/migrations/*_core.sql`: schema, wait-window formula (PRD 13.1), allocation (10.3), ordering and
  fairness guard (10.4), patient RPCs, staff actions with mandatory reasons (S10), realtime broadcasts, RLS.
- `*_demo.sql`: demo clock, seed generator, autopilot.
- `*_dashboard.sql`: dashboard read models.
