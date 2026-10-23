-- Role screens: the doctor's desk, follow-ups, and the district overview (PRD 11.1, 11.2, 12.1, 12.3, 13.2, 13.6, 13.7).
-- All history below is synthetic demo data (S9).

-- ---------------------------------------------------------------------------
-- Health Journey: one staff-confirmed status per visit (only a doctor or nurse sets it, PRD 11.1)
-- ---------------------------------------------------------------------------
create table public.health_journey (
  id uuid primary key default gen_random_uuid(),
  visit_id uuid not null unique references public.visits on delete cascade,
  patient_id uuid not null references public.patients on delete cascade,
  hospital_id text not null references public.hospitals,
  doctor_id text references public.doctors,
  status text not null check (status in ('red','yellow','green')),
  follow_up_on date,
  consult_ok boolean not null default false,
  notes text check (notes is null or char_length(notes) <= 500),
  recorded_by text not null,
  created_at timestamptz not null default public.app_now()
);
create index health_journey_patient on public.health_journey (patient_id, created_at);
create index health_journey_followup on public.health_journey (hospital_id, follow_up_on) where follow_up_on is not null;

create table public.followup_actions (
  id uuid primary key default gen_random_uuid(),
  journey_id uuid not null references public.health_journey on delete cascade,
  action text not null check (action in ('called','sms','community')),
  note text check (note is null or char_length(note) <= 280),
  actor text not null,
  created_at timestamptz not null default public.app_now()
);

-- Demand history for the forecast (13.2): visits per department per hour of past days.
create table public.demand_history (
  hospital_id text not null references public.hospitals,
  department_id text not null references public.departments,
  day date not null,
  hour int not null check (hour between 0 and 23),
  visits int not null,
  primary key (department_id, day, hour)
);

-- Symptom-cluster counts by area and day for the outbreak view (13.6). Anonymous, aggregate only.
create table public.cluster_history (
  area text not null,
  cluster text not null,
  day date not null,
  cases int not null,
  primary key (area, cluster, day)
);

alter table public.health_journey enable row level security;
alter table public.followup_actions enable row level security;
alter table public.demand_history enable row level security;
alter table public.cluster_history enable row level security;
create policy "staff read" on public.health_journey for select to authenticated using (public.staff_can_see(hospital_id));
create policy "staff read" on public.followup_actions for select to authenticated using (public.is_staff());
create policy "staff read" on public.demand_history for select to authenticated using (public.is_staff());
create policy "staff read" on public.cluster_history for select to authenticated using (public.is_staff());

alter table public.hospitals alter column settings set default
  '{"fairness_wait_min":90,"fairness_overtaken":5,"no_show_grace_min":5,"baseline_wait_min":120}'::jsonb;
update public.hospitals set settings = settings || '{"baseline_wait_min":120}'::jsonb;

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------
create or replace function public.patient_journey(p_patient uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object(
           'visit_id', j.visit_id, 'day', v.day, 'hospital', h.short_name, 'department', dep.name,
           'doctor', d.name, 'status', j.status, 'follow_up_on', j.follow_up_on, 'consult_ok', j.consult_ok,
           'notes', j.notes) order by v.day, j.created_at), '[]'::jsonb)
  from public.health_journey j
  join public.visits v on v.id = j.visit_id
  join public.hospitals h on h.id = j.hospital_id
  join public.departments dep on dep.id = v.department_id
  left join public.doctors d on d.id = j.doctor_id
  where j.patient_id = p_patient
$$;

-- A follow-up is done once the patient has any later visit; overdue (defaulted) once its date passes without one.
create or replace function public.followup_state(j public.health_journey) returns text
language sql stable set search_path = public as $$
  select case
    when j.follow_up_on is null then null
    when exists (select 1 from public.visits v
                  where v.patient_id = j.patient_id and v.day > (select day from public.visits where id = j.visit_id)
                    and v.day <= j.follow_up_on + 7) then 'done'
    when j.follow_up_on < public.app_today() then 'defaulted'
    else 'due' end
$$;

-- ---------------------------------------------------------------------------
-- Doctor's desk (12.1 doctor view)
-- ---------------------------------------------------------------------------
create or replace function public.get_doctor_desk(p_doctor_id text default null) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  s public.staff := public.current_staff();
  v_doc text;
  d public.doctors;
  v_today date := public.app_today();
begin
  if s.user_id is null then raise exception 'staff login required'; end if;
  v_doc := case when s.role = 'doctor' then s.doctor_id else coalesce(p_doctor_id, 'H01-D01') end;
  select * into d from public.doctors where id = v_doc;
  if d.id is null then raise exception 'doctor not found'; end if;
  if not public.staff_can_see(d.hospital_id) then raise exception 'not your hospital'; end if;

  return jsonb_build_object(
    'now', public.app_now(),
    'doctor', jsonb_build_object('id', d.id, 'name', d.name, 'room', d.room, 'floor', d.floor, 'status', d.status,
                                 'status_note', d.status_note, 'hospital_id', d.hospital_id,
                                 'department', (select name from public.departments where id = d.department_id),
                                 'consult_min', round(public.doctor_consult_minutes(d.id)::numeric, 1),
                                 'seen_today', (select count(*) from public.visits v where v.doctor_id = d.id and v.day = v_today and v.status = 'done')),
    'doctors', (select jsonb_agg(jsonb_build_object('id', x.id, 'name', x.name, 'room', x.room) order by x.id)
                  from public.doctors x where public.staff_can_see(x.hospital_id) and (s.role <> 'doctor' or x.id = s.doctor_id)),
    'serving', (
      select jsonb_build_object(
        'visit_id', v.id, 'token', v.token, 'first_name', p.first_name, 'age_group', p.age_group, 'age_years', p.age_years,
        'special_needs', p.special_needs, 'language', p.language, 'band', v.band, 'provisional', v.confirmed_band is null,
        'called_at', v.called_at, 'doctor_note', v.doctor_note,
        'journey', public.patient_journey(p.id),
        'recorded', (select to_jsonb(j) from public.health_journey j where j.visit_id = v.id))
      from public.visits v join public.patients p on p.id = v.patient_id
      where v.doctor_id = d.id and v.day = v_today and v.status = 'called'
      order by v.called_at desc limit 1),
    'queue', coalesce((
      select jsonb_agg(jsonb_build_object(
        'visit_id', q.id, 'position', q.position, 'token', q.token, 'first_name', p.first_name, 'age_group', p.age_group,
        'age_years', p.age_years, 'band', q.band, 'provisional', q.confirmed_band is null, 'arrived_at', q.arrived_at,
        'doctor_note', q.doctor_note, 'special_needs', p.special_needs,
        'history', (select coalesce(jsonb_agg(j.status order by v2.day), '[]'::jsonb)
                      from public.health_journey j join public.visits v2 on v2.id = j.visit_id
                     where j.patient_id = p.id and v2.day < v_today)) order by q.position)
      from public.queue_positions q join public.patients p on p.id = q.patient_id
      where q.doctor_id = d.id), '[]'::jsonb),
    'followups', coalesce((
      select jsonb_agg(jsonb_build_object('journey_id', j.id, 'first_name', p.first_name, 'status', j.status,
                                          'follow_up_on', j.follow_up_on, 'state', public.followup_state(j)) order by j.follow_up_on)
      from public.health_journey j join public.patients p on p.id = j.patient_id
      where j.doctor_id = d.id and j.follow_up_on between v_today - 14 and v_today + 3
        and public.followup_state(j) in ('due','defaulted')), '[]'::jsonb)
  );
end $$;

-- The doctor records this visit: staff-confirmed status, follow-up, text-consult suitability (11.1, 11.2, 11.3).
create or replace function public.doctor_record(p jsonb) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  s public.staff := public.current_staff();
  v public.visits;
  v_status text := p->>'status';
  v_days int := nullif(p->>'follow_up_days', '')::int;
begin
  if s.user_id is null or s.role not in ('doctor','triage','admin','demo') then raise exception 'doctor or nurse login required'; end if;
  select * into v from public.visits where id = (p->>'visit_id')::uuid;
  if v.id is null then raise exception 'visit not found'; end if;
  if not public.staff_can_see(v.hospital_id) then raise exception 'not your hospital'; end if;
  if s.role = 'doctor' and v.doctor_id is distinct from s.doctor_id then raise exception 'not your patient'; end if;
  if v_status not in ('red','yellow','green') then raise exception 'status must be red, yellow or green'; end if;
  if v_days is not null and (v_days < 1 or v_days > 180) then raise exception 'follow-up must be 1 to 180 days'; end if;

  insert into public.health_journey (visit_id, patient_id, hospital_id, doctor_id, status, follow_up_on, consult_ok, notes, recorded_by)
  values (v.id, v.patient_id, v.hospital_id, v.doctor_id, v_status,
          case when v_days is null then null else v.day + v_days end,
          coalesce((p->>'consult_ok')::boolean, false), left(p->>'notes', 500), s.name)
  on conflict (visit_id) do update set
    status = excluded.status, follow_up_on = excluded.follow_up_on, consult_ok = excluded.consult_ok,
    notes = excluded.notes, recorded_by = excluded.recorded_by;

  insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, actor_role, to_value)
  values (v.hospital_id, v.id, v.doctor_id, 'journey_status', s.name, s.role,
          jsonb_build_object('status', v_status, 'follow_up_days', v_days));
  return jsonb_build_object('ok', true);
end $$;

-- ---------------------------------------------------------------------------
-- Follow-ups (11.2): due, done and defaulted, with the staff actions taken
-- ---------------------------------------------------------------------------
create or replace function public.get_followups(p_hospital_id text) returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.staff_can_see(p_hospital_id) then raise exception 'staff login required for this hospital'; end if;
  return coalesce((
    select jsonb_agg(row_to_json(x) order by x.state_rank, x.follow_up_on)
    from (
      select j.id as journey_id, p.first_name, p.age_group, p.language,
             case when p.phone is null then null else '•••• ' || right(p.phone, 4) end as phone,
             j.status, j.follow_up_on, j.consult_ok, d.name as doctor, dep.name as department, v.day as last_visit,
             public.followup_state(j) as state,
             case public.followup_state(j) when 'defaulted' then 0 when 'due' then 1 else 2 end as state_rank,
             (public.app_today() - j.follow_up_on) as days_late,
             (select coalesce(jsonb_agg(jsonb_build_object('action', a.action, 'actor', a.actor, 'at', a.created_at, 'note', a.note)
                                        order by a.created_at desc), '[]'::jsonb)
                from public.followup_actions a where a.journey_id = j.id) as actions
      from public.health_journey j
      join public.patients p on p.id = j.patient_id
      join public.visits v on v.id = j.visit_id
      join public.departments dep on dep.id = v.department_id
      left join public.doctors d on d.id = j.doctor_id
      where j.hospital_id = p_hospital_id and j.follow_up_on is not null
        and j.follow_up_on >= public.app_today() - 30
        -- only the latest journey entry per patient carries an open follow-up
        and not exists (select 1 from public.health_journey j2 join public.visits v2 on v2.id = j2.visit_id
                         where j2.patient_id = j.patient_id and v2.day > v.day)
    ) x), '[]'::jsonb);
end $$;

create or replace function public.followup_action(p jsonb) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  s public.staff := public.current_staff();
  j public.health_journey;
begin
  if s.user_id is null then raise exception 'staff login required'; end if;
  select * into j from public.health_journey where id = (p->>'journey_id')::uuid;
  if j.id is null then raise exception 'follow-up not found'; end if;
  if not public.staff_can_see(j.hospital_id) then raise exception 'not your hospital'; end if;
  insert into public.followup_actions (journey_id, action, note, actor)
  values (j.id, p->>'action', left(p->>'note', 280), s.name);
  return jsonb_build_object('ok', true);
end $$;

-- ---------------------------------------------------------------------------
-- District overview (12.3): load, balancing, forecast, outbreak signal, follow-ups, impact
-- ---------------------------------------------------------------------------
create or replace function public.get_district() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  s public.staff := public.current_staff();
  v_now timestamptz := public.app_now();
  v_today date := public.app_today();
  -- the next day the OPD opens (it is closed on Sundays)
  v_tomorrow date := public.app_today() + case when extract(isodow from public.app_today() + 1) = 7 then 2 else 1 end;
begin
  if s.user_id is null or s.hospital_id is not null then raise exception 'district login required'; end if;

  return jsonb_build_object(
    'now', v_now,
    'tomorrow', v_tomorrow,
    'hospitals', (
      select jsonb_agg(jsonb_build_object(
        'id', h.id, 'name', h.name, 'short_name', h.short_name, 'district', h.district, 'services', h.services,
        'waiting', (select count(*) from public.visits v where v.hospital_id = h.id and v.day = v_today and v.status = 'waiting'),
        'seen', (select count(*) from public.visits v where v.hospital_id = h.id and v.day = v_today and v.status = 'done'),
        'doctors_in', (select count(*) from public.doctors d where d.hospital_id = h.id and d.status = 'in'),
        'doctors_total', (select count(*) from public.doctors d where d.hospital_id = h.id and d.status <> 'off'),
        'avg_waited_min', (select round(avg(extract(epoch from (v_now - v.arrived_at)) / 60)) from public.visits v
                            where v.hospital_id = h.id and v.day = v_today and v.status = 'waiting'),
        'open_flags', (select count(*) from public.urgent_flags f where f.hospital_id = h.id and f.seen_at is null),
        'departments', (
          select jsonb_agg(jsonb_build_object('id', dep.id, 'code', dep.code, 'name', dep.name, 'waiting', w.n,
                                              'level', public.overcrowding_level(w.n, dep.amber_at, dep.red_at)) order by dep.code)
          from public.departments dep
          cross join lateral (select count(*)::int as n from public.visits v
                               where v.department_id = dep.id and v.day = v_today and v.status = 'waiting') w
          where dep.hospital_id = h.id)) order by h.id)
      from public.hospitals h),
    -- 13.7: a department Red at one hospital while the same service is Green nearby; routine remote registrations only.
    'balancing', coalesce((
      select jsonb_agg(jsonb_build_object('from', hb.short_name, 'to', hq.short_name, 'department', b.name,
                                          'from_waiting', b.n, 'to_waiting', q.n,
                                          'km', round((111.0 * sqrt(power(hb.lat - hq.lat, 2) + power((hb.lng - hq.lng) * cos(radians(hb.lat)), 2)))::numeric, 1)))
      from (select dep.*, (select count(*) from public.visits v where v.department_id = dep.id and v.day = v_today and v.status = 'waiting')::int as n
              from public.departments dep) b
      join (select dep.*, (select count(*) from public.visits v where v.department_id = dep.id and v.day = v_today and v.status = 'waiting')::int as n
              from public.departments dep) q
        on q.code = b.code and q.hospital_id <> b.hospital_id
      join public.hospitals hb on hb.id = b.hospital_id
      join public.hospitals hq on hq.id = q.hospital_id
      where public.overcrowding_level(b.n, b.amber_at, b.red_at) = 'red'
        and public.overcrowding_level(q.n, q.amber_at, q.red_at) = 'green'), '[]'::jsonb),
    -- 13.2: same weekday over the last six weeks, recent weeks weighted more, as a range per hour.
    'forecast', (
      with past as (
        select dh.hospital_id, dh.hour, dh.day, sum(dh.visits) as visits
        from public.demand_history dh
        where extract(isodow from dh.day) = extract(isodow from v_tomorrow) and dh.day >= v_tomorrow - 42 and dh.day < v_today
        group by 1, 2, 3
      ), stats as (
        select hospital_id, hour,
               sum(visits * (7 - ((v_tomorrow - day) / 7))) / sum(7 - ((v_tomorrow - day) / 7)) as mean,
               coalesce(stddev_samp(visits), 0) as sd
        from past group by 1, 2
      )
      select jsonb_agg(jsonb_build_object(
        'hospital_id', h.id, 'short_name', h.short_name,
        'capacity_per_hour', (select round(sum(60.0 / public.doctor_consult_minutes(d.id))) from public.doctors d where d.hospital_id = h.id and d.status <> 'off'),
        'hours', (select jsonb_agg(jsonb_build_object('hour', st.hour, 'mid', round(st.mean), 'lo', greatest(0, round(st.mean - 1.28 * st.sd)),
                                                      'hi', round(st.mean + 1.28 * st.sd)) order by st.hour)
                    from stats st where st.hospital_id = h.id),
        'total', (select round(sum(st.mean)) from stats st where st.hospital_id = h.id)) order by h.id)
      from public.hospitals h),
    -- 13.6: today against the area's last four weeks; cells under five cases are never shown.
    'outbreak', (
      with base as (
        select area, cluster, avg(cases) as mean, coalesce(stddev_samp(cases), 0) as sd
        from public.cluster_history where day between v_today - 28 and v_today - 1
        group by 1, 2
      ), today as (
        select area, cluster, sum(n)::int as cases from (
          select area, cluster, cases as n from public.cluster_history where day = v_today
          union all
          select area, cluster, 1 from public.symptom_checks
           where (created_at at time zone 'Asia/Kolkata')::date = v_today and area is not null and cluster is not null) t
        group by 1, 2
      )
      select jsonb_build_object(
        'signals', coalesce(jsonb_agg(jsonb_build_object('area', t.area, 'cluster', t.cluster, 'today', t.cases,
                                                          'usual', round(b.mean, 1), 'sd', round(b.sd, 1),
                                                          'z', round((t.cases - b.mean) / nullif(b.sd, 0), 1))
                                       order by (t.cases - b.mean) / nullif(b.sd, 0) desc nulls last)
                            filter (where t.cases >= 5 and t.cases > b.mean + 2 * greatest(b.sd, 1)), '[]'::jsonb),
        'watched', count(*) filter (where t.cases >= 5),
        'suppressed', count(*) filter (where t.cases < 5))
      from today t left join base b using (area, cluster)),
    'followups', (
      select jsonb_build_object(
        'due', count(*) filter (where public.followup_state(j) = 'due'),
        'done', count(*) filter (where public.followup_state(j) = 'done'),
        'defaulted', count(*) filter (where public.followup_state(j) = 'defaulted'))
      from public.health_journey j where j.follow_up_on between v_today - 30 and v_today + 14),
    -- Impact (demo counter): today's waits against each hospital's pre-SevaSetu baseline.
    'impact', (
      select jsonb_build_object(
        'patients', count(*),
        'avg_wait_min', round(avg(extract(epoch from (v.called_at - v.arrived_at)) / 60)),
        'baseline_wait_min', round(avg((h.settings->>'baseline_wait_min')::numeric)),
        'hours_saved', round(sum(greatest(0, (h.settings->>'baseline_wait_min')::numeric
                                             - extract(epoch from (v.called_at - v.arrived_at)) / 60)) / 60))
      from public.visits v join public.hospitals h on h.id = v.hospital_id
      where v.day = v_today and v.called_at is not null)
  );
end $$;

revoke execute on function public.get_doctor_desk(text), public.doctor_record(jsonb), public.get_followups(text),
  public.followup_action(jsonb), public.get_district() from anon, public;
grant execute on function public.get_doctor_desk(text), public.doctor_record(jsonb), public.get_followups(text),
  public.followup_action(jsonb), public.get_district() to authenticated;

-- ---------------------------------------------------------------------------
-- Demo data: six weeks of history, and returning patients for Dr. Lone (Room 3)
-- ---------------------------------------------------------------------------
create or replace function public.demo_seed_history() returns void
language plpgsql security definer set search_path = public as $$
declare
  v_today date := public.app_today();
  dep record;
  d date;
  hr int;
  base numeric;
  areas text[] := array['Anantnag','Pulwama','Kulgam','Shopian','Bijbehara','Pahalgam'];
  clusters text[] := array['fever','respiratory','diarrhoea','skin','injury'];
  a text;
  c text;
  m numeric;
begin
  delete from public.demand_history;
  delete from public.cluster_history;
  for dep in select dp.*, (select count(*) from public.doctors x where x.department_id = dp.id) as docs from public.departments dp loop
    for d in select generate_series(v_today - 42, v_today - 1, interval '1 day')::date loop
      continue when extract(isodow from d) = 7;                -- OPD closed on Sundays
      for hr in 9..15 loop
        -- Mondays busiest, the morning peak at 10-11, scaled by how many doctors the department has.
        -- The district hospital draws the most patients per doctor; the community centre the fewest.
        base := dep.docs * (case dep.hospital_id when 'H01' then 6.8 when 'H02' then 5.5 else 3.5 end)
              * (case extract(isodow from d) when 1 then 1.35 when 2 then 1.15 when 6 then 0.8 else 1.0 end)
              * (case hr when 9 then 0.8 when 10 then 1.35 when 11 then 1.3 when 12 then 1.0 when 13 then 0.7 when 14 then 0.75 else 0.5 end);
        insert into public.demand_history values (dep.hospital_id, dep.id, d, hr, greatest(0, round(base * (0.8 + random() * 0.4))));
      end loop;
    end loop;
  end loop;

  foreach a in array areas loop
    foreach c in array clusters loop
      m := case c when 'fever' then 6 when 'respiratory' then 7 when 'diarrhoea' then 3 when 'skin' then 2 else 3 end;
      for d in select generate_series(v_today - 28, v_today, interval '1 day')::date loop
        insert into public.cluster_history values (a, c, d, greatest(0, round(m * (0.6 + random() * 0.8))));
      end loop;
    end loop;
  end loop;
  -- Today's signal for the demo: loose motions in Pulwama, well above its usual 2-4 a day.
  update public.cluster_history set cases = 15 where area = 'Pulwama' and cluster = 'diarrhoea' and day = v_today;
end $$;

create or replace function public.demo_seed_journeys() returns void
language plpgsql security definer set search_path = public as $$
declare
  v_today date := public.app_today();
  v_now timestamptz := public.app_now();
  r record;
  v_pid uuid;
  v_vid uuid;
  v_tok record;
  i int;
  past_day date;
begin
  -- name, age, needs, language, statuses on past visits (oldest first), follow-up days after the last, visit today?
  for r in select * from (values
    ('Kamla', 62, array['walking'], 'ks', array['red','yellow','green'], 30, true,  'Chest infection, admitted once; recovering well'),
    ('Ghulam', 71, array['walking'], 'ks', array['yellow','yellow'], 14, true,     'Blood pressure not yet controlled; tablets changed'),
    ('Shabnam', 29, array['pregnant'], 'ur', array['green'], 7, true,               'Antenatal check, all normal'),
    ('Rakesh', 54, array[]::text[], 'doi', array['yellow','green'], 21, true,       'Diabetes review; sugars better'),
    ('Nusrat', 45, array[]::text[], 'ur', array['yellow'], 5, false,                'Fever, review in five days'),
    ('Farooq', 66, array['hearing'], 'ks', array['red','yellow'], 3, false,         'Chest pain ruled out; follow up'),
    ('Saima', 38, array[]::text[], 'hi', array['green'], 10, false,                 'Thyroid check')
  ) as t(name, age, needs, lang, statuses, follow_days, today_visit, note) loop
    v_pid := gen_random_uuid();
    insert into public.patients (id, first_name, age_years, age_group, special_needs, language, phone, area, consent, created_at)
    values (v_pid, r.name, r.age, case when r.age >= 60 then 'elderly' else 'adult' end, r.needs, r.lang,
            '98' || lpad((floor(random() * 99999999))::text, 8, '0'),
            (array['Anantnag','Pulwama','Kulgam','Bijbehara'])[1 + floor(random() * 4)::int],
            '{"terms":true,"movement":true}', v_now - interval '60 days');
    for i in 1..array_length(r.statuses, 1) loop
      -- past visits spaced back from today; the last one sets the follow-up that falls due around now
      past_day := v_today - r.follow_days - (array_length(r.statuses, 1) - i) * 21
                  + case when r.today_visit then 0 else -2 end;
      v_vid := gen_random_uuid();
      select * into v_tok from public.next_token('H01-GM', past_day);
      insert into public.visits (id, patient_id, hospital_id, department_id, doctor_id, day, token, token_seq, channel,
                                 suggested_band, confirmed_band, band_source, status, arrived_at, sort_at, called_at, finished_at,
                                 created_at, updated_at, doctor_note)
      values (v_vid, v_pid, 'H01', 'H01-GM', 'H01-D01', past_day, v_tok.token, v_tok.seq, 'app', 'green', 'green', 'staff', 'done',
              (past_day + time '10:00') at time zone 'Asia/Kolkata', (past_day + time '10:00') at time zone 'Asia/Kolkata',
              (past_day + time '10:40') at time zone 'Asia/Kolkata', (past_day + time '10:48') at time zone 'Asia/Kolkata',
              (past_day + time '10:00') at time zone 'Asia/Kolkata', (past_day + time '10:48') at time zone 'Asia/Kolkata', r.note);
      insert into public.health_journey (visit_id, patient_id, hospital_id, doctor_id, status, follow_up_on, consult_ok, notes, recorded_by, created_at)
      values (v_vid, v_pid, 'H01', 'H01-D01', r.statuses[i],
              case when i = array_length(r.statuses, 1) then past_day + r.follow_days else past_day + 21 end,
              r.statuses[i] = 'green', r.note, 'Dr. Aamir Lone', (past_day + time '10:48') at time zone 'Asia/Kolkata');
    end loop;
    if r.today_visit then
      v_vid := gen_random_uuid();
      select * into v_tok from public.next_token('H01-GM', v_today);
      insert into public.visits (id, patient_id, hospital_id, department_id, doctor_id, day, token, token_seq, channel,
                                 suggested_band, confirmed_band, band_source, status, arrived_at, sort_at, doctor_note, created_at, updated_at)
      values (v_vid, v_pid, 'H01', 'H01-GM', 'H01-D01', v_today, v_tok.token, v_tok.seq, 'app',
              case when r.age >= 60 or 'pregnant' = any(r.needs) then 'yellow' else 'green' end, null, 'app', 'waiting',
              -- Kamla arrived first, so she is the next patient called in the demo
              v_now - make_interval(mins => case when r.name = 'Kamla' then 95 else 40 + floor(random() * 30)::int end),
              v_now - make_interval(mins => case when r.name = 'Kamla' then 95 else 40 + floor(random() * 30)::int end),
              'Follow-up visit: ' || lower(r.note), v_now, v_now);
      insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, to_value)
      values ('H01', v_vid, 'H01-D01', 'join', 'demo', jsonb_build_object('token', v_tok.token));
    end if;
  end loop;
end $$;

-- demo_reset now also clears and rebuilds the history behind the new screens.
create or replace function public.demo_reset_all(p_sim_time time default '10:30') returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  res jsonb;
begin
  if auth.uid() is not null then perform public.require_demo_operator(); end if;
  perform set_config('sevasetu.allow_reset', 'on', true);
  delete from public.followup_actions;
  delete from public.health_journey;
  res := public.demo_reset(p_sim_time);
  perform public.demo_seed_history();
  perform public.demo_seed_journeys();
  return res;
end $$;

revoke execute on function public.demo_seed_history(), public.demo_seed_journeys() from anon, public, authenticated;
revoke execute on function public.demo_reset_all(time) from anon, public;
grant execute on function public.demo_reset_all(time) to authenticated;

-- The patient app's read-only Health Journey (11.1 patient view). The visit id is the bearer secret,
-- as for get_my_turn; it returns only what staff recorded, never an app-generated statement.
create or replace function public.get_my_journey(p_visit_id uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'first_name', p.first_name,
    'journey', public.patient_journey(p.id),
    'next_follow_up', (select jsonb_build_object('on', j.follow_up_on, 'consult_ok', j.consult_ok, 'state', public.followup_state(j))
                         from public.health_journey j join public.visits v2 on v2.id = j.visit_id
                        where j.patient_id = p.id and j.follow_up_on is not null
                        order by v2.day desc limit 1))
  from public.visits v join public.patients p on p.id = v.patient_id
  where v.id = p_visit_id
$$;
grant execute on function public.get_my_journey(uuid) to anon, authenticated;
