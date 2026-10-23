-- SevaSetu core schema: hospitals, queue, urgent flags, transparency log.
-- All business rules live in SQL functions so the patient app and the
-- dashboard enforce exactly the same safety principles (PRD section 4).

create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------------------
-- Demo clock. Every timestamp uses app_now() so demo controls can speed up
-- or jump time without touching real clocks (PRD 12.1, demo controls).
-- ---------------------------------------------------------------------------
create table public.demo_clock (
  id int primary key default 1 check (id = 1),
  anchor_real timestamptz not null default clock_timestamp(),
  anchor_sim timestamptz not null default clock_timestamp(),
  speed double precision not null default 1 check (speed > 0 and speed <= 600)
);
insert into public.demo_clock default values;

create or replace function public.app_now() returns timestamptz
language sql stable set search_path = public as $$
  select anchor_sim + (clock_timestamp() - anchor_real) * speed from public.demo_clock where id = 1
$$;

create or replace function public.app_today() returns date
language sql stable set search_path = public as $$
  select (public.app_now() at time zone 'Asia/Kolkata')::date
$$;

-- ---------------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------------
create table public.hospitals (
  id text primary key,                         -- 'H01'
  name text not null,
  short_name text not null,
  district text not null,
  address text not null,
  lat double precision not null,
  lng double precision not null,
  phone text,
  opd_hours text not null default '09:00-16:00',
  services jsonb not null default '{}'::jsonb,  -- {"lab":"up","xray":"down","pharmacy":"up"}
  settings jsonb not null default '{"fairness_wait_min":90,"fairness_overtaken":5,"no_show_grace_min":5}'::jsonb,
  graph_version int not null default 1
);

create table public.departments (
  id text primary key,                         -- 'H01-GM'
  hospital_id text not null references public.hospitals,
  code text not null,                          -- token prefix, e.g. 'M' -> M-017
  name text not null,
  name_i18n jsonb not null default '{}'::jsonb,
  amber_at int not null default 15,            -- overcrowding thresholds (waiting count)
  red_at int not null default 30,
  default_consult_min double precision not null default 6,
  unique (hospital_id, code)
);

create table public.doctors (
  id text primary key,                         -- 'H01-D01'
  hospital_id text not null references public.hospitals,
  department_id text not null references public.departments,
  name text not null,
  room text not null,
  floor int not null default 0,
  room_waypoint_id text,
  stair_free boolean not null default true,
  status text not null default 'in' check (status in ('in','break','away','delayed','off')),
  status_note text,
  status_changed_at timestamptz not null default now(),
  default_consult_min double precision
);

create table public.staff (
  user_id uuid primary key references auth.users on delete cascade,
  name text not null,
  role text not null check (role in ('counter','triage','doctor','admin','demo')),
  hospital_id text references public.hospitals,   -- null = all hospitals (district view)
  doctor_id text references public.doctors
);

-- ---------------------------------------------------------------------------
-- Patients and visits
-- ---------------------------------------------------------------------------
create table public.patients (
  id uuid primary key,                          -- generated on the device
  abha_ref text,                                -- mocked in the demo (S9)
  first_name text not null check (char_length(first_name) between 1 and 60),
  age_years int check (age_years between 0 and 120),
  age_group text not null check (age_group in ('infant','child','adult','elderly')),
  special_needs text[] not null default '{}',
  language text not null default 'hi' check (language in ('ks','ur','doi','hi','en')),
  phone text check (phone is null or phone ~ '^[0-9+ ]{6,16}$'),
  area text,
  consent jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default public.app_now()
);

create table public.visits (
  id uuid primary key,                          -- generated on the device
  patient_id uuid not null references public.patients,
  hospital_id text not null references public.hospitals,
  department_id text not null references public.departments,
  doctor_id text references public.doctors,
  day date not null,
  token text not null,
  token_seq int not null,
  offline_token text,
  channel text not null check (channel in ('app','counter','sms','remote','demo')),
  suggested_band text not null default 'green' check (suggested_band in ('green','yellow')),
  confirmed_band text check (confirmed_band in ('green','yellow','red')),
  band text generated always as (coalesce(confirmed_band, suggested_band)) stored,
  band_source text not null default 'app' check (band_source in ('app','patient','staff')),
  status text not null default 'waiting'
    check (status in ('waiting','called','done','emergency','cancelled')),
  needs_stair_free boolean not null default false,
  needs_visual_call boolean not null default false,
  doctor_note text check (doctor_note is null or char_length(doctor_note) <= 500),
  arrived_at timestamptz not null,
  sort_at timestamptz not null,                 -- ordering key within a band; staff moves change it
  called_at timestamptz,
  finished_at timestamptz,
  call_count int not null default 0,
  no_show_count int not null default 0,
  created_at timestamptz not null default public.app_now(),
  updated_at timestamptz not null default public.app_now(),
  unique (hospital_id, day, token)
);
create index visits_doctor_day_status on public.visits (doctor_id, day, status);
create index visits_hospital_day on public.visits (hospital_id, day);

create table public.token_counters (
  department_id text not null references public.departments,
  day date not null,
  last_seq int not null default 0,
  primary key (department_id, day)
);

create table public.symptom_checks (
  id uuid primary key,
  patient_id uuid references public.patients,
  visit_id uuid references public.visits,
  hospital_id text references public.hospitals,
  ruleset_version text not null,
  language text,
  answers jsonb not null default '{}'::jsonb,
  outcome text not null check (outcome in ('emergency','department','help_desk')),
  department_code text,
  suggested_band text,
  cluster text,                                 -- coarse symptom cluster for outbreak view (13.6)
  area text,
  created_at timestamptz not null default public.app_now()
);

create table public.urgent_flags (
  id uuid primary key,                          -- generated on the device
  hospital_id text not null references public.hospitals,
  visit_id uuid references public.visits,
  session_id text,
  source text not null check (source in ('patient_app','symptom_check','sms','counter','demo')),
  message text check (message is null or char_length(message) <= 280),
  waypoint_id text,
  raised_at timestamptz not null,
  received_at timestamptz not null default public.app_now(),
  seen_by text,
  seen_at timestamptz
);
create index urgent_flags_open on public.urgent_flags (hospital_id) where seen_at is null;

-- Transparency log (PRD 10.5). Append-only.
create table public.queue_events (
  id uuid primary key default gen_random_uuid(),
  hospital_id text not null references public.hospitals,
  visit_id uuid references public.visits,
  doctor_id text references public.doctors,
  type text not null,
  actor text not null,
  actor_role text not null default 'system',
  reason_code text,
  note text,
  from_value jsonb,
  to_value jsonb,
  patient_message text,
  created_at timestamptz not null default public.app_now()
);
create index queue_events_hospital_time on public.queue_events (hospital_id, created_at desc);
create index queue_events_visit_time on public.queue_events (visit_id, created_at desc);
create index queue_events_doctor_time on public.queue_events (doctor_id, created_at desc);

create or replace function public.forbid_change() returns trigger language plpgsql as $$
begin
  raise exception 'queue_events is append-only';
end $$;
create trigger queue_events_append_only before update or delete on public.queue_events
  for each row when (current_setting('sevasetu.allow_reset', true) is distinct from 'on')
  execute function public.forbid_change();

-- Anonymous indoor movement (PRD 9.6). Never joined to identity.
create table public.journey_events (
  id uuid primary key,
  session_id text not null,
  hospital_id text not null references public.hospitals,
  type text not null check (type in ('entry','scan','room','wrong_turn','exit')),
  waypoint_id text,
  occurred_at timestamptz not null,
  received_at timestamptz not null default public.app_now()
);
create index journey_events_hospital_time on public.journey_events (hospital_id, occurred_at desc);

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------
create or replace function public.band_rank(b text) returns int
language sql immutable as $$
  select case b when 'red' then 0 when 'yellow' then 1 else 2 end
$$;

create or replace function public.band_label(b text) returns text
language sql immutable as $$
  select case b when 'red' then 'Emergency now' when 'yellow' then 'Priority' else 'Routine' end
$$;

create or replace function public.current_staff() returns public.staff
language sql stable security definer set search_path = public as $$
  select * from public.staff where user_id = auth.uid()
$$;

create or replace function public.is_staff() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.staff where user_id = auth.uid())
$$;

-- Today's waiting queue with positions. Red is never queued (10.4).
create or replace view public.queue_positions with (security_invoker = true) as
select v.*,
       row_number() over (partition by v.doctor_id order by public.band_rank(v.band), v.sort_at, v.token_seq) as position
from public.visits v
where v.status = 'waiting' and v.day = public.app_today();

-- Rolling median consultation time over the last 20 consultations (13.1).
create or replace function public.doctor_consult_minutes(p_doctor text) returns double precision
language sql stable set search_path = public as $$
  with recent as (
    select extract(epoch from (finished_at - called_at)) / 60.0 as m
    from public.visits
    where doctor_id = p_doctor and status = 'done' and called_at is not null and finished_at is not null
    order by finished_at desc
    limit 20
  )
  select coalesce(
    (select case when count(*) >= 5 then percentile_cont(0.5) within group (order by m) end from recent),
    (select coalesce(d.default_consult_min, dep.default_consult_min)
       from public.doctors d join public.departments dep on dep.id = d.department_id
      where d.id = p_doctor),
    6)
$$;

-- Estimate and time window (13.1): ahead x median, margin = max(10 min, 25%), rounded to 5.
create or replace function public.visit_estimate(p_visit uuid) returns jsonb
language plpgsql stable set search_path = public as $$
declare
  v public.visits;
  d public.doctors;
  v_pos int;
  v_ahead int;
  v_mins double precision;
  v_est double precision;
  v_margin double precision;
  v_now timestamptz := public.app_now();
  v_start timestamptz;
  v_end timestamptz;
begin
  select * into v from public.visits where id = p_visit;
  if v.id is null or v.status <> 'waiting' then
    return null;
  end if;
  select * into d from public.doctors where id = v.doctor_id;
  select position into v_pos from public.queue_positions where id = p_visit;
  v_ahead := coalesce(v_pos, 1) - 1;
  v_mins := case when d.id is null then 6 else public.doctor_consult_minutes(d.id) end;
  v_est := v_ahead * v_mins;
  -- someone currently with the doctor: assume half a consultation remains
  if exists (select 1 from public.visits where doctor_id = v.doctor_id and status = 'called' and day = v.day) then
    v_est := v_est + v_mins / 2;
  end if;
  v_margin := greatest(10, round(v_est * 0.25 / 5) * 5);
  v_start := date_bin('5 minutes', greatest(v_now, v_now + make_interval(mins => (v_est - v_margin)::int)), timestamptz '2000-01-01');
  v_end := date_bin('5 minutes', v_now + make_interval(mins => (v_est + v_margin)::int) + interval '4 minutes 59 seconds', timestamptz '2000-01-01');
  return jsonb_build_object(
    'people_ahead', v_ahead,
    'estimate_min', round(v_est::numeric),
    'window_start', v_start,
    'window_end', v_end,
    'paused', coalesce(d.status in ('break','away','off'), true),
    'consult_min', round(v_mins::numeric, 1)
  );
end $$;

-- Pick the on-duty doctor with the shortest estimated wait (10.3 step 3).
create or replace function public.allocate_doctor(p_department text, p_preferred text default null) returns text
language sql stable set search_path = public as $$
  select d.id
  from public.doctors d
  where d.department_id = p_department and d.status <> 'off'
  order by
    (d.id = p_preferred and d.status in ('in','delayed')) desc,
    case d.status when 'in' then 0 when 'delayed' then 1 when 'break' then 2 else 3 end,
    (select count(*) from public.visits v
      where v.doctor_id = d.id and v.status = 'waiting' and v.day = public.app_today())
      * public.doctor_consult_minutes(d.id),
    d.id
  limit 1
$$;

create or replace function public.next_token(p_department text, p_day date, out token text, out seq int)
language plpgsql set search_path = public as $$
begin
  insert into public.token_counters (department_id, day, last_seq) values (p_department, p_day, 1)
  on conflict (department_id, day) do update set last_seq = public.token_counters.last_seq + 1
  returning last_seq into seq;
  select dep.code || '-' || lpad(seq::text, 3, '0') into token from public.departments dep where dep.id = p_department;
end $$;

-- ---------------------------------------------------------------------------
-- Patient-facing RPCs (callable with the anon key by the patient app)
-- ---------------------------------------------------------------------------

-- Slip / My Turn. The visit id is the bearer secret; output shows first name only (S9).
create or replace function public.get_my_turn(p_visit_id uuid) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  v public.visits;
  p public.patients;
  d public.doctors;
  dep public.departments;
  h public.hospitals;
  v_serving text;
  v_reason jsonb;
  v_flag jsonb;
begin
  select * into v from public.visits where id = p_visit_id;
  if v.id is null then
    return null;
  end if;
  select * into p from public.patients where id = v.patient_id;
  select * into d from public.doctors where id = v.doctor_id;
  select * into dep from public.departments where id = v.department_id;
  select * into h from public.hospitals where id = v.hospital_id;

  select x.token into v_serving from public.visits x
   where x.doctor_id = v.doctor_id and x.day = v.day and x.status = 'called'
   order by x.called_at desc limit 1;

  -- Most recent reason the patient should see (S10): own events, or doctor-level events in the last 30 min.
  select jsonb_build_object('type', e.type, 'reason_code', e.reason_code, 'message', e.patient_message, 'at', e.created_at)
    into v_reason
    from public.queue_events e
   where e.patient_message is not null
     and ((e.visit_id = v.id and e.type <> 'priority_join')
          or (e.doctor_id = v.doctor_id and e.visit_id is distinct from v.id
              and e.type in ('doctor_status','priority_join') and e.created_at > public.app_now() - interval '30 minutes'))
   order by e.created_at desc limit 1;

  select jsonb_build_object('id', f.id, 'raised_at', f.raised_at, 'received_at', f.received_at, 'seen_at', f.seen_at)
    into v_flag
    from public.urgent_flags f where f.visit_id = v.id order by f.raised_at desc limit 1;

  return jsonb_build_object(
    'visit_id', v.id,
    'hospital', jsonb_build_object('id', h.id, 'name', h.name, 'address', h.address, 'lat', h.lat, 'lng', h.lng),
    'token', v.token,
    'offline_token', v.offline_token,
    'first_name', p.first_name,
    'department', jsonb_build_object('id', dep.id, 'code', dep.code, 'name', dep.name, 'name_i18n', dep.name_i18n),
    'doctor', case when d.id is null then null else jsonb_build_object(
        'id', d.id, 'name', d.name, 'room', d.room, 'floor', d.floor, 'room_waypoint_id', d.room_waypoint_id,
        'stair_free', d.stair_free, 'status', d.status, 'status_note', d.status_note) end,
    'band', v.band,
    'band_label', public.band_label(v.band),
    'band_provisional', v.confirmed_band is null,
    'status', v.status,
    'now_serving', v_serving,
    'estimate', public.visit_estimate(v.id),
    'last_reason', v_reason,
    'urgent_flag', v_flag,
    'wait_anywhere_allowed', (v.band = 'green' and v.confirmed_band is distinct from 'yellow'
                              and not exists (select 1 from public.urgent_flags f where f.visit_id = v.id)),
    'slip_qr', 'SS1:SLIP:' || v.hospital_id || ':' || v.id,
    'last_updated', public.app_now()
  );
end $$;

-- Registration (PRD 8.2, 10.3). Idempotent on visit_id so the offline outbox can resend safely.
create or replace function public.register_visit(p jsonb) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_visit_id uuid := (p->>'visit_id')::uuid;
  v_pat jsonb := p->'patient';
  v_patient_id uuid := (v_pat->>'id')::uuid;
  v_hospital text := p->>'hospital_id';
  v_dept text;
  v_band text := coalesce(p->>'suggested_band', 'green');
  v_needs text[];
  v_age_group text;
  v_doctor text;
  v_tok record;
  v_now timestamptz := public.app_now();
  v_arrived timestamptz;
  v_channel text := coalesce(p->>'channel', 'app');
  v_offline text := nullif(p->>'offline_token', '');
  v_actor_role text := case when public.is_staff() then 'counter' else 'patient' end;
begin
  if v_visit_id is null or v_patient_id is null then
    raise exception 'visit_id and patient.id are required (generate UUIDs on the device)';
  end if;
  if exists (select 1 from public.visits where id = v_visit_id) then
    return public.get_my_turn(v_visit_id);   -- already ingested: safe resend
  end if;
  if not exists (select 1 from public.hospitals where id = v_hospital) then
    raise exception 'unknown hospital_id %', v_hospital;
  end if;
  if v_channel = 'counter' and not public.is_staff() then
    raise exception 'counter registration requires a staff login';
  end if;
  if v_band not in ('green','yellow') then
    raise exception 'suggested_band must be green or yellow; emergencies are never queued (S2)';
  end if;

  select id into v_dept from public.departments
   where hospital_id = v_hospital and (id = p->>'department_id' or code = p->>'department_code');
  if v_dept is null then
    raise exception 'unknown department for hospital %', v_hospital;
  end if;

  v_needs := coalesce(array(select jsonb_array_elements_text(v_pat->'special_needs')), '{}');
  v_age_group := coalesce(v_pat->>'age_group', 'adult');

  insert into public.patients (id, abha_ref, first_name, age_years, age_group, special_needs, language, phone, area, consent)
  values (v_patient_id, v_pat->>'abha_ref', trim(v_pat->>'first_name'), (v_pat->>'age_years')::int, v_age_group,
          v_needs, coalesce(v_pat->>'language', 'hi'), nullif(v_pat->>'phone', ''), v_pat->>'area',
          coalesce(v_pat->'consent', '{}'::jsonb))
  on conflict (id) do update set
    first_name = excluded.first_name, age_years = excluded.age_years, age_group = excluded.age_group,
    special_needs = excluded.special_needs, language = excluded.language,
    phone = coalesce(excluded.phone, public.patients.phone), area = coalesce(excluded.area, public.patients.area),
    consent = excluded.consent;

  -- Inputs to the suggested band (10.2): age group, pregnancy. Never lowers what the app suggested (S3).
  if v_age_group in ('infant','elderly') or 'pregnant' = any(v_needs) then
    v_band := 'yellow';
  end if;

  v_doctor := public.allocate_doctor(v_dept, p->>'preferred_doctor_id');
  select * into v_tok from public.next_token(v_dept, public.app_today());

  -- Arrival is server time. Only offline-issued tokens keep their device time, bounded to 45 minutes,
  -- so a client cannot jump the queue by backdating (fairness, 10.4).
  v_arrived := case when v_offline is null then v_now
                    else least(v_now, greatest(coalesce((p->>'created_at')::timestamptz, v_now), v_now - interval '45 minutes')) end;

  insert into public.visits (id, patient_id, hospital_id, department_id, doctor_id, day, token, token_seq,
                             offline_token, channel, suggested_band, band_source, needs_stair_free,
                             needs_visual_call, doctor_note, arrived_at, sort_at)
  values (v_visit_id, v_patient_id, v_hospital, v_dept, v_doctor, public.app_today(), v_tok.token, v_tok.seq,
          v_offline, v_channel, v_band, case when p ? 'patient_set_band' then 'patient' else 'app' end,
          v_needs && array['walking','wheelchair'],
          v_needs && array['hearing'],
          left(p->>'doctor_note', 500), v_arrived, v_arrived);

  insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, actor_role, to_value, patient_message)
  values (v_hospital, v_visit_id, v_doctor, 'join', coalesce(p->>'actor', v_channel), v_actor_role,
          jsonb_build_object('token', v_tok.token, 'band', v_band, 'doctor_id', v_doctor), null);

  if v_offline is not null and v_offline <> v_tok.token then
    insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, reason_code, from_value, to_value, patient_message)
    values (v_hospital, v_visit_id, v_doctor, 'token_confirmed', 'system', 'token_confirmed',
            jsonb_build_object('token', v_offline), jsonb_build_object('token', v_tok.token),
            'Your token is confirmed. It changed from ' || v_offline || ' to ' || v_tok.token || '.');
  end if;

  if v_band = 'yellow' then
    insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, reason_code, patient_message)
    values (v_hospital, v_visit_id, v_doctor, 'priority_join', 'system', 'priority_patient',
            'A Priority patient joined the queue ahead of Routine patients.');
  end if;

  if p ? 'symptom_check' then
    insert into public.symptom_checks (id, patient_id, visit_id, hospital_id, ruleset_version, language, answers,
                                       outcome, department_code, suggested_band, cluster, area)
    values (coalesce((p->'symptom_check'->>'id')::uuid, gen_random_uuid()), v_patient_id, v_visit_id, v_hospital,
            coalesce(p->'symptom_check'->>'ruleset_version', 'unknown'), v_pat->>'language',
            coalesce(p->'symptom_check'->'answers', '{}'::jsonb), 'department',
            p->'symptom_check'->>'department_code', v_band, p->'symptom_check'->>'cluster', v_pat->>'area')
    on conflict (id) do nothing;
  end if;

  return public.get_my_turn(v_visit_id);
end $$;

-- Emergency outcome of a symptom check is still recorded (anonymously usable for 13.6) but never queued (S2).
create or replace function public.record_symptom_check(p jsonb) returns void
language plpgsql security definer set search_path = public as $$
begin
  if (p->>'outcome') not in ('emergency','department','help_desk') then
    raise exception 'invalid outcome';
  end if;
  insert into public.symptom_checks (id, hospital_id, ruleset_version, language, answers, outcome,
                                     department_code, suggested_band, cluster, area, created_at)
  values ((p->>'id')::uuid, p->>'hospital_id', coalesce(p->>'ruleset_version', 'unknown'), p->>'language',
          coalesce(p->'answers', '{}'::jsonb), p->>'outcome', p->>'department_code', p->>'suggested_band',
          p->>'cluster', p->>'area', coalesce((p->>'created_at')::timestamptz, public.app_now()))
  on conflict (id) do nothing;
end $$;

-- "I need urgent help" (S5). Idempotent on id.
create or replace function public.raise_urgent_flag(p jsonb) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_id uuid := (p->>'id')::uuid;
  v_visit uuid := (p->>'visit_id')::uuid;
  v_hospital text := p->>'hospital_id';
  f public.urgent_flags;
begin
  if v_id is null then
    raise exception 'id is required (generate a UUID on the device)';
  end if;
  if v_visit is not null then
    select hospital_id into v_hospital from public.visits where id = v_visit;
  end if;
  if v_hospital is null or not exists (select 1 from public.hospitals where id = v_hospital) then
    raise exception 'hospital_id or a valid visit_id is required';
  end if;
  insert into public.urgent_flags (id, hospital_id, visit_id, session_id, source, message, waypoint_id, raised_at)
  values (v_id, v_hospital, v_visit, p->>'session_id', coalesce(p->>'source', 'patient_app'),
          left(p->>'message', 280), p->>'waypoint_id',
          least(public.app_now(), coalesce((p->>'raised_at')::timestamptz, public.app_now())))
  on conflict (id) do nothing;
  select * into f from public.urgent_flags where id = v_id;
  return jsonb_build_object('id', f.id, 'received_at', f.received_at, 'seen_at', f.seen_at, 'seen_by', f.seen_by);
end $$;

create or replace function public.get_urgent_flag(p_id uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('id', id, 'received_at', received_at, 'seen_at', seen_at, 'seen_by', seen_by)
  from public.urgent_flags where id = p_id
$$;

-- "I have my slip" by typed token (8.7). The QR path uses the visit id directly.
create or replace function public.link_slip(p_hospital_id text, p_token text) returns jsonb
language sql stable security definer set search_path = public as $$
  select public.get_my_turn(v.id) from public.visits v
  where v.hospital_id = p_hospital_id and v.token = upper(trim(p_token)) and v.day = public.app_today()
$$;

-- Anonymous journey events (9.6). Bulk, idempotent.
create or replace function public.ingest_journey_events(p_events jsonb) returns int
language plpgsql security definer set search_path = public as $$
declare
  n int;
begin
  insert into public.journey_events (id, session_id, hospital_id, type, waypoint_id, occurred_at)
  select (e->>'id')::uuid, e->>'session_id', e->>'hospital_id', e->>'type', e->>'waypoint_id',
         least(public.app_now(), (e->>'occurred_at')::timestamptz)
  from jsonb_array_elements(p_events) e
  on conflict (id) do nothing;
  get diagnostics n = row_count;
  return n;
end $$;

-- Public waiting-room board (12.1): token and first name only (S9).
create or replace function public.get_board(p_hospital_id text) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'hospital', (select jsonb_build_object('id', id, 'name', name) from public.hospitals where id = p_hospital_id),
    'now', public.app_now(),
    'doctors', coalesce(jsonb_agg(jsonb_build_object(
      'doctor_id', d.id, 'doctor_name', d.name, 'room', d.room, 'floor', d.floor, 'status', d.status,
      'department', dep.name,
      'serving', (select jsonb_build_object('token', v.token, 'first_name', p.first_name, 'called_at', v.called_at,
                                            'visual_call', v.needs_visual_call, 'call_count', v.call_count)
                     from public.visits v join public.patients p on p.id = v.patient_id
                    where v.doctor_id = d.id and v.status = 'called' and v.day = public.app_today()
                    order by v.called_at desc limit 1),
      'next', coalesce((select jsonb_agg(q.token order by q.position)
                          from public.queue_positions q where q.doctor_id = d.id and q.position <= 3), '[]'::jsonb)
    ) order by dep.code, d.room), '[]'::jsonb))
  from public.doctors d join public.departments dep on dep.id = d.department_id
  where d.hospital_id = p_hospital_id and d.status <> 'off'
$$;

create or replace function public.get_clock() returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('now', public.app_now(), 'speed', speed, 'today', public.app_today())
  from public.demo_clock where id = 1
$$;

-- ---------------------------------------------------------------------------
-- Staff actions (12.2). Every change is logged with a reason (S10).
-- ---------------------------------------------------------------------------
create or replace function public.reason_message(p_reason text) returns text
language sql immutable as $$
  select case p_reason
    when 'priority_patient' then 'Moved: a Priority patient was seen first.'
    when 'clinical_review' then 'Your place changed after review by hospital staff.'
    when 'doctor_delayed' then 'The doctor is delayed.'
    when 'doctor_unavailable' then 'Your doctor is not available, so you were moved to another doctor.'
    when 'wrong_department' then 'You were moved to the right department.'
    when 'patient_request' then 'Changed at your request.'
    when 'no_show' then 'You were called but we could not find you. You are now at the end of the queue.'
    when 'load_balance' then 'Moved to a doctor with a shorter queue.'
    when 'staff_correction' then 'Corrected by hospital staff.'
    else 'Your place in the queue was changed by hospital staff.'
  end
$$;

create or replace function public.staff_action(p jsonb) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  s public.staff := public.current_staff();
  v_action text := p->>'action';
  v_reason text := p->>'reason_code';
  v_note text := left(p->>'note', 280);
  v public.visits;
  n public.visits;
  d public.doctors;
  v_now timestamptz := public.app_now();
  v_tok record;
  v_target text;
  v_tmp timestamptz;
begin
  if s.user_id is null then
    raise exception 'staff login required';
  end if;
  if v_action in ('no_show','move_end','move_up','move_down','move_department','assign_doctor','set_band','send_to_emergency')
     and coalesce(v_reason, '') = '' then
    raise exception 'a reason is required for %', v_action;
  end if;

  if p ? 'visit_id' then
    select * into v from public.visits where id = (p->>'visit_id')::uuid for update;
    if v.id is null then raise exception 'visit not found'; end if;
    if s.hospital_id is not null and v.hospital_id <> s.hospital_id then raise exception 'not your hospital'; end if;
    if s.role = 'doctor' and v.doctor_id is distinct from s.doctor_id and v_action not in ('set_band','send_to_emergency') then
      raise exception 'doctors can only manage their own queue';
    end if;
  end if;

  case v_action
  when 'call_next' then
    select * into d from public.doctors where id = p->>'doctor_id';
    if d.id is null then raise exception 'doctor not found'; end if;
    -- finish whoever is currently with the doctor
    with done as (
      update public.visits set status = 'done', finished_at = v_now, updated_at = v_now
       where doctor_id = d.id and status = 'called' and day = public.app_today()
       returning id, hospital_id, doctor_id)
    insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, actor_role)
    select hospital_id, id, doctor_id, 'complete', s.name, s.role from done;

    select q.* into n from public.queue_positions q where q.doctor_id = d.id order by q.position limit 1;
    if n.id is null then
      return jsonb_build_object('ok', true, 'called', null);
    end if;
    update public.visits set status = 'called', called_at = v_now, call_count = call_count + 1, updated_at = v_now
     where id = n.id;
    insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, actor_role, to_value, patient_message)
    values (n.hospital_id, n.id, d.id, 'call', s.name, s.role, jsonb_build_object('room', d.room),
            'It is your turn. Please go to ' || d.room || '.');
    return jsonb_build_object('ok', true, 'called', n.token, 'visit_id', n.id);

  when 'recall' then
    if v.status <> 'called' then raise exception 'patient is not currently called'; end if;
    update public.visits set call_count = call_count + 1, called_at = v_now, updated_at = v_now where id = v.id;
    insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, actor_role, patient_message)
    values (v.hospital_id, v.id, v.doctor_id, 'recall', s.name, s.role, 'You are being called again. Please come now.');

  when 'complete' then
    update public.visits set status = 'done', finished_at = v_now, updated_at = v_now where id = v.id;
    insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, actor_role)
    values (v.hospital_id, v.id, v.doctor_id, 'complete', s.name, s.role);

  when 'no_show' then
    -- Staff action, never automatic (8.6). Patient is offered the end of the queue.
    update public.visits set status = 'waiting', called_at = null, sort_at = v_now,
           no_show_count = no_show_count + 1, updated_at = v_now where id = v.id;
    insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, actor_role, reason_code, note, patient_message)
    values (v.hospital_id, v.id, v.doctor_id, 'no_show', s.name, s.role, v_reason, v_note, public.reason_message('no_show'));

  when 'move_end' then
    update public.visits set status = 'waiting', sort_at = v_now, updated_at = v_now where id = v.id;
    insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, actor_role, reason_code, note, patient_message)
    values (v.hospital_id, v.id, v.doctor_id, 'move_end', s.name, s.role, v_reason, v_note, public.reason_message(v_reason));

  when 'move_up', 'move_down' then
    if v.status <> 'waiting' then raise exception 'only waiting patients can be moved'; end if;
    select q.* into n from public.queue_positions q
     where q.doctor_id = v.doctor_id
       and q.position = (select position from public.queue_positions where id = v.id)
                        + case when v_action = 'move_up' then -1 else 1 end;
    if n.id is null then raise exception 'already at the % of the queue', case when v_action = 'move_up' then 'top' else 'end' end; end if;
    if n.band <> v.band then raise exception 'cannot move across bands; change the band instead'; end if;
    v_tmp := v.sort_at;
    update public.visits set sort_at = n.sort_at, updated_at = v_now where id = v.id;
    update public.visits set sort_at = v_tmp, updated_at = v_now where id = n.id;
    insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, actor_role, reason_code, note, patient_message)
    values (v.hospital_id, v.id, v.doctor_id, v_action, s.name, s.role, v_reason, v_note, public.reason_message(v_reason)),
           (n.hospital_id, n.id, n.doctor_id, case when v_action = 'move_up' then 'move_down' else 'move_up' end,
            s.name, s.role, v_reason, 'swapped with ' || v.token, public.reason_message(v_reason));

  when 'set_band', 'confirm_band' then
    v_target := coalesce(p->>'band', v.band);
    if v_target not in ('green','yellow') then raise exception 'band must be green or yellow; use send_to_emergency for red'; end if;
    update public.visits set confirmed_band = v_target, band_source = 'staff', updated_at = v_now where id = v.id;
    insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, actor_role, reason_code, note,
                                     from_value, to_value, patient_message)
    values (v.hospital_id, v.id, v.doctor_id, v_action, s.name, s.role, v_reason, v_note,
            jsonb_build_object('band', v.band, 'confirmed', v.confirmed_band is not null),
            jsonb_build_object('band', v_target, 'confirmed', true),
            case when v_target = v.band then 'Staff confirmed your status: ' || public.band_label(v_target) || '.'
                 else 'Staff changed your status to ' || public.band_label(v_target) || '.' end);

  when 'send_to_emergency' then
    update public.visits set status = 'emergency', confirmed_band = 'red', band_source = 'staff', updated_at = v_now where id = v.id;
    insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, actor_role, reason_code, note,
                                     from_value, to_value, patient_message)
    values (v.hospital_id, v.id, v.doctor_id, 'send_to_emergency', s.name, s.role, v_reason, v_note,
            jsonb_build_object('band', v.band), jsonb_build_object('band', 'red'),
            'Please go to Emergency now. Staff are expecting you.');

  when 'move_department' then
    select id into v_target from public.departments where id = p->>'department_id' and hospital_id = v.hospital_id;
    if v_target is null then raise exception 'department not found in this hospital'; end if;
    select * into v_tok from public.next_token(v_target, v.day);
    update public.visits set department_id = v_target, doctor_id = public.allocate_doctor(v_target),
           token = v_tok.token, token_seq = v_tok.seq, status = 'waiting', updated_at = v_now
     where id = v.id returning * into n;
    insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, actor_role, reason_code, note,
                                     from_value, to_value, patient_message)
    values (v.hospital_id, v.id, n.doctor_id, 'move_department', s.name, s.role, v_reason, v_note,
            jsonb_build_object('department_id', v.department_id, 'token', v.token, 'doctor_id', v.doctor_id),
            jsonb_build_object('department_id', n.department_id, 'token', n.token, 'doctor_id', n.doctor_id),
            public.reason_message(v_reason) || ' Your new token is ' || n.token || '.');

  when 'assign_doctor' then
    select * into d from public.doctors where id = p->>'doctor_id' and department_id = v.department_id;
    if d.id is null then raise exception 'doctor not found in this department'; end if;
    update public.visits set doctor_id = d.id, updated_at = v_now where id = v.id;
    insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, actor_role, reason_code, note,
                                     from_value, to_value, patient_message)
    values (v.hospital_id, v.id, d.id, 'assign_doctor', s.name, s.role, v_reason, v_note,
            jsonb_build_object('doctor_id', v.doctor_id), jsonb_build_object('doctor_id', d.id),
            public.reason_message(v_reason) || ' Please go to ' || d.room || ' when called.');

  when 'set_doctor_status' then
    select * into d from public.doctors where id = p->>'doctor_id';
    if d.id is null then raise exception 'doctor not found'; end if;
    if s.hospital_id is not null and d.hospital_id <> s.hospital_id then raise exception 'not your hospital'; end if;
    if (p->>'status') not in ('in','break','away','delayed','off') then raise exception 'invalid doctor status'; end if;
    update public.doctors set status = p->>'status', status_note = v_note, status_changed_at = v_now where id = d.id;
    insert into public.queue_events (hospital_id, doctor_id, type, actor, actor_role, reason_code, note,
                                     from_value, to_value, patient_message)
    values (d.hospital_id, d.id, 'doctor_status', s.name, s.role, v_reason, v_note,
            jsonb_build_object('status', d.status), jsonb_build_object('status', p->>'status'),
            case p->>'status'
              when 'break' then 'The doctor is on a short break. Your time window is paused.'
              when 'away' then 'The doctor is away for now. Your time window is paused.'
              when 'delayed' then 'The doctor is running late' || coalesce(': ' || v_note, '.')
              when 'in' then 'The doctor is back and seeing patients.'
              else 'The doctor has finished for the day. Staff will guide you.' end);

  else
    raise exception 'unknown action %', v_action;
  end case;

  return jsonb_build_object('ok', true);
end $$;

create or replace function public.ack_urgent_flag(p_id uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  s public.staff := public.current_staff();
  f public.urgent_flags;
begin
  if s.user_id is null then raise exception 'staff login required'; end if;
  update public.urgent_flags set seen_at = public.app_now(), seen_by = s.name
   where id = p_id and seen_at is null returning * into f;
  if f.id is null then select * into f from public.urgent_flags where id = p_id; end if;
  return jsonb_build_object('id', f.id, 'seen_at', f.seen_at, 'seen_by', f.seen_by,
                            'ack_seconds', extract(epoch from (f.seen_at - f.received_at)));
end $$;

-- Fairness guard (10.4): alerts only, never reorders.
create or replace function public.fairness_alerts(p_hospital_id text) returns table (
  visit_id uuid, token text, doctor_id text, waited_min int, overtaken int)
language sql stable security definer set search_path = public as $$
  with cfg as (select (settings->>'fairness_wait_min')::int as wait_min,
                      (settings->>'fairness_overtaken')::int as overtaken
                 from public.hospitals where id = p_hospital_id)
  select q.id, q.token, q.doctor_id,
         (extract(epoch from (public.app_now() - q.arrived_at)) / 60)::int,
         (select count(*)::int from public.visits o
           where o.doctor_id = q.doctor_id and o.day = q.day and o.called_at is not null
             and o.arrived_at > q.arrived_at and o.called_at >= q.arrived_at)
  from public.queue_positions q, cfg
  where q.hospital_id = p_hospital_id and q.band = 'green' and public.is_staff()
    and ((extract(epoch from (public.app_now() - q.arrived_at)) / 60) > cfg.wait_min
         or (select count(*) from public.visits o
              where o.doctor_id = q.doctor_id and o.day = q.day and o.called_at is not null
                and o.arrived_at > q.arrived_at and o.called_at >= q.arrived_at) > cfg.overtaken)
$$;

-- ---------------------------------------------------------------------------
-- Realtime: broadcast so the patient app (anon) can follow its own visit
-- without reading tables. Topics: 'hospital:<id>' and 'visit:<uuid>'.
-- ---------------------------------------------------------------------------
create or replace function public.broadcast_visit_change() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  r record := coalesce(new, old);
begin
  perform realtime.send(jsonb_build_object('doctor_id', r.doctor_id, 'visit_id', r.id),
                        'queue_changed', 'hospital:' || r.hospital_id, false);
  perform realtime.send(jsonb_build_object('visit_id', r.id, 'status', r.status),
                        'visit_changed', 'visit:' || r.id, false);
  return null;
end $$;
create trigger visits_broadcast after insert or update on public.visits
  for each row execute function public.broadcast_visit_change();

create or replace function public.broadcast_doctor_change() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform realtime.send(jsonb_build_object('doctor_id', new.id, 'status', new.status),
                        'queue_changed', 'hospital:' || new.hospital_id, false);
  return null;
end $$;
create trigger doctors_broadcast after update on public.doctors
  for each row execute function public.broadcast_doctor_change();

create or replace function public.broadcast_flag_change() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  -- Wake every dashboard (S5). The payload carries no patient data; staff refetch through RLS.
  perform realtime.send(jsonb_build_object('flag_id', new.id), 'queue_changed', 'hospital:' || new.hospital_id, false);
  if tg_op = 'UPDATE' and new.seen_at is not null and old.seen_at is null then
    if new.visit_id is not null then
      perform realtime.send(jsonb_build_object('flag_id', new.id, 'seen_at', new.seen_at),
                            'flag_seen', 'visit:' || new.visit_id, false);
    end if;
    perform realtime.send(jsonb_build_object('flag_id', new.id, 'seen_at', new.seen_at),
                          'flag_seen', 'flag:' || new.id, false);
  end if;
  return null;
end $$;
create trigger urgent_flags_broadcast after insert or update on public.urgent_flags
  for each row execute function public.broadcast_flag_change();

-- Dashboard uses postgres_changes (authenticated staff, filtered by RLS).
alter publication supabase_realtime add table public.visits, public.urgent_flags, public.doctors, public.queue_events;

-- ---------------------------------------------------------------------------
-- Row level security: patients never read tables directly; staff read all
-- rows for their hospital (or every hospital for district roles).
-- ---------------------------------------------------------------------------
alter table public.demo_clock enable row level security;
alter table public.hospitals enable row level security;
alter table public.departments enable row level security;
alter table public.doctors enable row level security;
alter table public.staff enable row level security;
alter table public.patients enable row level security;
alter table public.visits enable row level security;
alter table public.token_counters enable row level security;
alter table public.symptom_checks enable row level security;
alter table public.urgent_flags enable row level security;
alter table public.queue_events enable row level security;
alter table public.journey_events enable row level security;

create or replace function public.staff_can_see(p_hospital text) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.staff s
                  where s.user_id = auth.uid() and (s.hospital_id is null or s.hospital_id = p_hospital))
$$;

-- Public reference data (hospital card, pre-visit check 8.10)
create policy "public read" on public.hospitals for select using (true);
create policy "public read" on public.departments for select using (true);
create policy "public read" on public.doctors for select using (true);
create policy "public read" on public.demo_clock for select using (true);

create policy "self read" on public.staff for select to authenticated using (user_id = auth.uid() or public.is_staff());
create policy "staff read" on public.patients for select to authenticated
  using (exists (select 1 from public.visits v where v.patient_id = patients.id and public.staff_can_see(v.hospital_id)));
create policy "staff read" on public.visits for select to authenticated using (public.staff_can_see(hospital_id));
create policy "staff read" on public.symptom_checks for select to authenticated using (public.staff_can_see(hospital_id));
create policy "staff read" on public.urgent_flags for select to authenticated using (public.staff_can_see(hospital_id));
create policy "staff read" on public.queue_events for select to authenticated using (public.staff_can_see(hospital_id));
create policy "staff read" on public.journey_events for select to authenticated using (public.staff_can_see(hospital_id));

revoke execute on function public.staff_action(jsonb), public.ack_urgent_flag(uuid) from anon, public;
grant execute on function public.staff_action(jsonb), public.ack_urgent_flag(uuid) to authenticated;
