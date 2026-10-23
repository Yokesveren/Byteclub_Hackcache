-- Dashboard read models. One round trip per refresh; realtime events trigger a refetch.

create or replace function public.overcrowding_level(p_waiting int, p_amber int, p_red int) returns text
language sql immutable as $$
  select case when p_waiting >= p_red then 'red' when p_waiting >= p_amber then 'amber' else 'green' end
$$;

-- Facility view (12.2): urgent flags, departments, doctor queues, fairness alerts.
create or replace function public.get_facility(p_hospital_id text) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  v_now timestamptz := public.app_now();
  v_today date := public.app_today();
begin
  if not public.staff_can_see(p_hospital_id) then
    raise exception 'staff login required for this hospital';
  end if;

  return jsonb_build_object(
    'now', v_now,
    'hospital', (select to_jsonb(h) from public.hospitals h where h.id = p_hospital_id),
    'urgent_flags', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', f.id, 'source', f.source, 'message', f.message, 'raised_at', f.raised_at,
        'received_at', f.received_at, 'waypoint_id', f.waypoint_id, 'session_id', f.session_id,
        'visit_id', f.visit_id, 'token', v.token, 'first_name', p.first_name, 'band', v.band,
        'doctor_name', d.name, 'room', d.room, 'floor', d.floor, 'needs_stair_free', v.needs_stair_free)
        order by f.received_at)
      from public.urgent_flags f
      left join public.visits v on v.id = f.visit_id
      left join public.patients p on p.id = v.patient_id
      left join public.doctors d on d.id = v.doctor_id
      where f.hospital_id = p_hospital_id and f.seen_at is null), '[]'::jsonb),
    'departments', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', dep.id, 'code', dep.code, 'name', dep.name, 'waiting', w.n,
        'amber_at', dep.amber_at, 'red_at', dep.red_at,
        'level', public.overcrowding_level(w.n, dep.amber_at, dep.red_at)) order by dep.code)
      from public.departments dep
      cross join lateral (select count(*)::int as n from public.visits v
                           where v.department_id = dep.id and v.status = 'waiting' and v.day = v_today) w
      where dep.hospital_id = p_hospital_id), '[]'::jsonb),
    'doctors', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', d.id, 'name', d.name, 'room', d.room, 'floor', d.floor, 'department_id', d.department_id,
        'status', d.status, 'status_note', d.status_note, 'status_changed_at', d.status_changed_at,
        'consult_min', round(public.doctor_consult_minutes(d.id)::numeric, 1),
        'seen_today', (select count(*) from public.visits v where v.doctor_id = d.id and v.day = v_today and v.status = 'done'),
        'serving', (select jsonb_build_object('visit_id', v.id, 'token', v.token, 'first_name', p.first_name,
                                              'band', v.band, 'called_at', v.called_at, 'call_count', v.call_count,
                                              'needs_visual_call', v.needs_visual_call, 'doctor_note', v.doctor_note)
                      from public.visits v join public.patients p on p.id = v.patient_id
                     where v.doctor_id = d.id and v.status = 'called' and v.day = v_today
                     order by v.called_at desc limit 1),
        'queue', coalesce((
          select jsonb_agg(jsonb_build_object(
            'visit_id', q.id, 'position', q.position, 'token', q.token, 'first_name', p.first_name,
            'age_group', p.age_group, 'age_years', p.age_years, 'special_needs', p.special_needs,
            'language', p.language, 'band', q.band, 'provisional', q.confirmed_band is null,
            'band_source', q.band_source, 'channel', q.channel, 'arrived_at', q.arrived_at,
            'waited_min', (extract(epoch from (v_now - q.arrived_at)) / 60)::int,
            'needs_stair_free', q.needs_stair_free, 'needs_visual_call', q.needs_visual_call,
            'no_show_count', q.no_show_count, 'doctor_note', q.doctor_note,
            'has_flag', exists (select 1 from public.urgent_flags f where f.visit_id = q.id))
            order by q.position)
          from public.queue_positions q join public.patients p on p.id = q.patient_id
          where q.doctor_id = d.id), '[]'::jsonb))
        order by d.department_id, d.id)
      from public.doctors d where d.hospital_id = p_hospital_id), '[]'::jsonb),
    'unassigned', coalesce((
      select jsonb_agg(jsonb_build_object('visit_id', v.id, 'token', v.token, 'department_id', v.department_id))
      from public.visits v where v.hospital_id = p_hospital_id and v.day = v_today
        and v.status = 'waiting' and v.doctor_id is null), '[]'::jsonb),
    'fairness', coalesce((select jsonb_agg(to_jsonb(a)) from public.fairness_alerts(p_hospital_id) a), '[]'::jsonb),
    'stats', jsonb_build_object(
      'waiting', (select count(*) from public.visits v where v.hospital_id = p_hospital_id and v.day = v_today and v.status = 'waiting'),
      'seen', (select count(*) from public.visits v where v.hospital_id = p_hospital_id and v.day = v_today and v.status = 'done'),
      'unconfirmed', (select count(*) from public.visits v where v.hospital_id = p_hospital_id and v.day = v_today
                        and v.status = 'waiting' and v.confirmed_band is null),
      'flags_today', (select count(*) from public.urgent_flags f where f.hospital_id = p_hospital_id
                        and (f.received_at at time zone 'Asia/Kolkata')::date = v_today),
      'median_ack_sec', (select round(percentile_cont(0.5) within group (order by extract(epoch from (f.seen_at - f.received_at)))::numeric, 1)
                           from public.urgent_flags f where f.hospital_id = p_hospital_id and f.seen_at is not null))
  );
end $$;

-- Transparency log (10.5), filterable.
create or replace function public.get_queue_log(p_hospital_id text, p_doctor_id text default null,
                                                p_type text default null, p_limit int default 200) returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.staff_can_see(p_hospital_id) then
    raise exception 'staff login required for this hospital';
  end if;
  return coalesce((
    select jsonb_agg(row_to_json(x) order by x.created_at desc) from (
      select e.id, e.created_at, e.type, e.actor, e.actor_role, e.reason_code, e.note, e.from_value, e.to_value,
             e.patient_message, e.doctor_id, d.name as doctor_name, v.token
      from public.queue_events e
      left join public.visits v on v.id = e.visit_id
      left join public.doctors d on d.id = e.doctor_id
      where e.hospital_id = p_hospital_id
        and (p_doctor_id is null or e.doctor_id = p_doctor_id)
        and (p_type is null or e.type = p_type)
        and e.actor <> 'demo'
      order by e.created_at desc
      limit least(p_limit, 1000)) x), '[]'::jsonb);
end $$;

revoke execute on function public.get_facility(text), public.get_queue_log(text, text, text, int) from anon, public;
grant execute on function public.get_facility(text), public.get_queue_log(text, text, text, int) to authenticated;
