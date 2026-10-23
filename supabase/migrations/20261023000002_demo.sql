-- Demo controls (PRD 12.1): reset, simulated patients, clock speed, autopilot, urgent flag.
-- All data generated here is made up (S9).

create or replace function public.require_demo_operator() returns public.staff
language plpgsql stable security definer set search_path = public as $$
declare
  s public.staff := public.current_staff();
begin
  if s.user_id is null or s.role not in ('demo','admin') then
    raise exception 'demo operator login required';
  end if;
  return s;
end $$;

create or replace function public.demo_first_names() returns text[]
language sql immutable as $$
  select array['Kamla','Rafiq','Shabnam','Ghulam','Mehraj','Sunita','Rakesh','Nusrat','Bilal','Asha',
               'Farooq','Rubina','Tariq','Anjali','Yusuf','Parveen','Ishfaq','Neelam','Javid','Saima',
               'Vikram','Rehana','Sajad','Kiran','Altaf','Shazia','Manzoor','Pooja','Imran','Haleema',
               'Arjun','Mubeena','Showkat','Rekha','Aijaz','Tabassum','Suresh','Nighat','Owais','Meena']
$$;

-- Insert one simulated visit directly (bypasses register_visit so history can be backdated).
create or replace function public.demo_insert_visit(
  p_hospital text, p_dept text, p_doctor text, p_arrived timestamptz, p_status text,
  p_band text, p_confirmed boolean, p_called timestamptz, p_finished timestamptz, p_name_idx int)
returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_pid uuid := gen_random_uuid();
  v_vid uuid := gen_random_uuid();
  v_names text[] := public.demo_first_names();
  v_age_group text;
  v_needs text[] := '{}';
  v_tok record;
  v_day date := (p_arrived at time zone 'Asia/Kolkata')::date;
  r double precision := random();
begin
  v_age_group := case when r < 0.04 then 'infant' when r < 0.14 then 'child' when r < 0.86 then 'adult' else 'elderly' end;
  if p_dept like '%-PD' then v_age_group := case when random() < 0.25 then 'infant' else 'child' end; end if;
  if p_dept like '%-MH' then v_needs := array['pregnant']; v_age_group := 'adult'; end if;
  if v_age_group = 'elderly' and random() < 0.4 then v_needs := v_needs || 'walking'::text; end if;
  if random() < 0.05 then v_needs := v_needs || 'hearing'::text; end if;

  insert into public.patients (id, first_name, age_group, age_years, special_needs, language, area, consent, created_at)
  values (v_pid, v_names[1 + (p_name_idx % array_length(v_names, 1))], v_age_group,
          case v_age_group when 'infant' then 0 when 'child' then 4 + (random() * 10)::int
                           when 'adult' then 20 + (random() * 38)::int else 60 + (random() * 25)::int end,
          v_needs, (array['ks','ur','doi','hi','en'])[1 + (random() * 4)::int],
          (array['Anantnag','Baramulla','Budgam','Ganderbal','Pulwama','Kupwara','Srinagar'])[1 + (random() * 6)::int],
          '{"terms":true,"movement":true}'::jsonb, p_arrived);

  select * into v_tok from public.next_token(p_dept, v_day);
  insert into public.visits (id, patient_id, hospital_id, department_id, doctor_id, day, token, token_seq, channel,
                             suggested_band, confirmed_band, band_source, status, needs_stair_free, needs_visual_call,
                             doctor_note, arrived_at, sort_at, called_at, finished_at, call_count, created_at, updated_at)
  values (v_vid, v_pid, p_hospital, p_dept, p_doctor, v_day, v_tok.token, v_tok.seq,
          (array['app','app','app','counter','counter','sms'])[1 + (random() * 5)::int],
          case when p_band = 'yellow' or v_age_group in ('infant','elderly') or 'pregnant' = any(v_needs) then 'yellow' else 'green' end,
          case when p_confirmed then case when p_band = 'yellow' or v_age_group in ('infant','elderly') or 'pregnant' = any(v_needs)
                                          then 'yellow' else 'green' end end,
          case when p_confirmed then 'staff' else 'app' end,
          p_status, v_needs && array['walking','wheelchair'], 'hearing' = any(v_needs),
          (array['Cough 5 days, fever, no breathing difficulty reported',
                 'Joint pain both knees, 3 weeks, walking slowly',
                 'Stomach pain and loose motions 2 days, eating and drinking',
                 'Headache on and off 1 week, no weakness reported',
                 'Fever 3 days with body ache',
                 'Skin rash on arms 10 days, itching',
                 'Follow-up visit, blood pressure check',
                 'Back pain after fall last week, can walk'])[1 + (random() * 7)::int],
          p_arrived, p_arrived, p_called, p_finished,
          case when p_called is null then 0 else 1 end, p_arrived, coalesce(p_finished, p_called, p_arrived));
  insert into public.queue_events (hospital_id, visit_id, doctor_id, type, actor, created_at, to_value)
  values (p_hospital, v_vid, p_doctor, 'join', 'demo', p_arrived, jsonb_build_object('token', v_tok.token));
  return v_vid;
end $$;

-- Wipe today's data and rebuild a realistic mid-morning OPD.
create or replace function public.demo_reset(p_sim_time time default '10:30') returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_today date := (now() at time zone 'Asia/Kolkata')::date;
  v_now timestamptz := (v_today + p_sim_time) at time zone 'Asia/Kolkata';
  d record;
  i int;
  n int := 0;
  v_waiting int;
  v_done int;
  t timestamptz;
  v_consult double precision;
begin
  if auth.uid() is not null then perform public.require_demo_operator(); end if;
  perform set_config('sevasetu.allow_reset', 'on', true);
  perform setseed(0.42);

  delete from public.journey_events;
  delete from public.urgent_flags;
  delete from public.symptom_checks;
  delete from public.queue_events;
  delete from public.visits;
  delete from public.patients;
  delete from public.token_counters;

  update public.demo_clock set anchor_real = clock_timestamp(), anchor_sim = v_now, speed = 1 where id = 1;
  update public.doctors set status = 'in', status_note = null, status_changed_at = v_now;
  update public.doctors set status = 'break', status_note = 'Back at 10:45' where id = 'H01-D03';
  update public.doctors set status = 'delayed', status_note = 'Ward round, about 20 minutes' where id = 'H02-D02';

  for d in select doc.*, dep.default_consult_min as dep_min from public.doctors doc
           join public.departments dep on dep.id = doc.department_id order by doc.id loop
    v_consult := coalesce(d.default_consult_min, d.dep_min);
    -- completed consultations since 09:00 give each doctor a real rolling median
    v_done := case when d.hospital_id = 'H01' then 10 + (random() * 6)::int else 6 + (random() * 4)::int end;
    t := (v_today + time '09:00') at time zone 'Asia/Kolkata';
    for i in 1..v_done loop
      perform public.demo_insert_visit(d.hospital_id, d.department_id, d.id,
                                       ((v_today + time '08:30') at time zone 'Asia/Kolkata') + i * interval '90 seconds', 'done',
                                       case when random() < 0.25 then 'yellow' else 'green' end, true,
                                       t, t + make_interval(secs => (v_consult * 60 * (0.6 + random() * 0.8))::int), n);
      t := t + make_interval(secs => (v_consult * 60 * (0.6 + random() * 0.8))::int) + interval '30 seconds';
      n := n + 1;
      exit when t > v_now - interval '8 minutes';
    end loop;
    -- the patient currently with the doctor
    if d.status in ('in','delayed') then
      perform public.demo_insert_visit(d.hospital_id, d.department_id, d.id, v_now - interval '50 minutes', 'called',
                                       'green', true, v_now - interval '3 minutes', null, n);
      n := n + 1;
    end if;
    -- the waiting queue: H01 busy, General Medicine busiest; H02 busy; H03 quiet
    v_waiting := case
      when d.department_id = 'H01-GM' then 7 + (random() * 4)::int
      when d.hospital_id = 'H01' then 3 + (random() * 3)::int
      when d.hospital_id = 'H02' then 6 + (random() * 4)::int
      else 1 + (random() * 2)::int end;
    for i in 1..v_waiting loop
      perform public.demo_insert_visit(d.hospital_id, d.department_id, d.id,
                                       v_now - make_interval(mins => (75 - i * 7 + (random() * 4)::int)),
                                       'waiting', case when random() < 0.08 then 'yellow' else 'green' end,
                                       random() < 0.6, null, null, n);
      n := n + 1;
    end loop;
  end loop;

  return jsonb_build_object('ok', true, 'patients', n, 'now', public.app_now());
end $$;

-- Add simulated walk-in patients through the real registration path.
create or replace function public.demo_add_patients(p_hospital text default 'H01', p_count int default 5) returns int
language plpgsql security definer set search_path = public as $$
declare
  i int;
  v_dept record;
  v_names text[] := public.demo_first_names();
  v_age text;
begin
  perform public.require_demo_operator();
  for i in 1..least(p_count, 50) loop
    select * into v_dept from public.departments where hospital_id = p_hospital order by random() limit 1;
    v_age := (array['child','adult','adult','adult','elderly'])[1 + (random() * 4)::int];
    perform public.register_visit(jsonb_build_object(
      'visit_id', gen_random_uuid(), 'hospital_id', p_hospital, 'department_id', v_dept.id, 'channel', 'demo',
      'suggested_band', case when random() < 0.12 then 'yellow' else 'green' end,
      'doctor_note', 'Simulated walk-in patient',
      'patient', jsonb_build_object('id', gen_random_uuid(), 'first_name', v_names[1 + (random() * 39)::int],
                                    'age_group', v_age, 'language', (array['ks','ur','doi','hi','en'])[1 + (random() * 4)::int],
                                    'special_needs', case when v_age = 'elderly' and random() < 0.5 then '["walking"]'::jsonb else '[]'::jsonb end,
                                    'consent', '{"terms":true,"movement":true}'::jsonb)));
  end loop;
  return least(p_count, 50);
end $$;

create or replace function public.demo_set_clock(p_speed double precision default null, p_jump_min int default 0) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  perform public.require_demo_operator();
  update public.demo_clock
     set anchor_sim = public.app_now() + make_interval(mins => coalesce(p_jump_min, 0)),
         anchor_real = clock_timestamp(),
         speed = coalesce(p_speed, speed)
   where id = 1;
  return public.get_clock();
end $$;

-- Autopilot: any doctor who is "in" and whose current consultation has run past
-- their usual time calls the next patient. Driven every few seconds by the demo page.
create or replace function public.demo_step(p_hospital text default null) returns int
language plpgsql security definer set search_path = public as $$
declare
  s public.staff := public.require_demo_operator();
  d record;
  n int := 0;
  v_called timestamptz;
begin
  for d in select * from public.doctors
            where status = 'in' and (p_hospital is null or hospital_id = p_hospital) loop
    select max(called_at) into v_called from public.visits
     where doctor_id = d.id and status = 'called' and day = public.app_today();
    if v_called is null
       or public.app_now() - v_called > make_interval(secs => (public.doctor_consult_minutes(d.id) * 60 * (0.7 + random() * 0.6))::int) then
      perform public.staff_action(jsonb_build_object('action', 'call_next', 'doctor_id', d.id));
      n := n + 1;
    end if;
  end loop;
  return n;
end $$;

create or replace function public.demo_trigger_flag(p_hospital text default 'H01') returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_visit uuid;
begin
  perform public.require_demo_operator();
  select id into v_visit from public.queue_positions where hospital_id = p_hospital order by random() limit 1;
  return public.raise_urgent_flag(jsonb_build_object(
    'id', gen_random_uuid(), 'hospital_id', p_hospital, 'visit_id', v_visit, 'source', 'demo',
    'message', 'Feeling worse: chest tightness while waiting (simulated)'));
end $$;

revoke execute on function public.demo_reset(time), public.demo_add_patients(text, int),
  public.demo_set_clock(double precision, int), public.demo_step(text), public.demo_trigger_flag(text),
  public.demo_insert_visit(text, text, text, timestamptz, text, text, boolean, timestamptz, timestamptz, int)
  from anon, public;
grant execute on function public.demo_reset(time), public.demo_add_patients(text, int),
  public.demo_set_clock(double precision, int), public.demo_step(text), public.demo_trigger_flag(text)
  to authenticated;
