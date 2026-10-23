-- Patient flow from anonymous QR-waypoint scans (PRD 9.6, 12.3): who is where right now, how long
-- the walk from the gate to a room takes, and where people take wrong turns. Aggregates only.

-- Where each connected visitor is now: their latest scan in the window, unless that scan was the exit.
create or replace function public.get_flow(p_hospital_id text, p_window_min int default 15) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  v_now timestamptz := public.app_now();
  v_from timestamptz := v_now - make_interval(mins => greatest(1, least(p_window_min, 240)));
  v_day timestamptz := (public.app_today()::timestamp) at time zone 'Asia/Kolkata';
begin
  if not public.staff_can_see(p_hospital_id) then
    raise exception 'staff login required for this hospital';
  end if;

  return jsonb_build_object(
    'now', v_now,
    'window_min', p_window_min,
    'here', coalesce((
      with latest as (
        select distinct on (session_id) session_id, type, waypoint_id
        from public.journey_events
        where hospital_id = p_hospital_id and occurred_at >= v_from
        order by session_id, occurred_at desc
      )
      select jsonb_object_agg(waypoint_id, n)
      from (select waypoint_id, count(*)::int as n from latest where type <> 'exit' and waypoint_id is not null group by 1) x
    ), '{}'::jsonb),
    'connected', (
      select count(*)::int from (
        select distinct on (session_id) session_id, type
        from public.journey_events
        where hospital_id = p_hospital_id and occurred_at >= v_from
        order by session_id, occurred_at desc
      ) s where s.type <> 'exit'),
    'today', (
      with s as (
        select session_id,
               min(occurred_at) filter (where type = 'entry') as entered,
               min(occurred_at) filter (where type = 'room') as reached,
               bool_or(type = 'exit') as left_building,
               count(*) filter (where type = 'wrong_turn') as wrong
        from public.journey_events
        where hospital_id = p_hospital_id and occurred_at >= v_day
        group by session_id
      )
      select jsonb_build_object(
        'journeys', count(*) filter (where entered is not null),
        'completed', count(*) filter (where left_building),
        'wrong_turns', coalesce(sum(wrong), 0),
        'wrong_turns_per_journey', round(coalesce(sum(wrong)::numeric / nullif(count(*) filter (where entered is not null), 0), 0), 2),
        'median_entry_to_room_min', round((percentile_cont(0.5) within group (
            order by extract(epoch from (reached - entered)) / 60) filter (where reached > entered))::numeric, 1)
      ) from s),
    'wrong_turn_spots', coalesce((
      select jsonb_agg(jsonb_build_object('waypoint_id', waypoint_id, 'count', n) order by n desc)
      from (
        select waypoint_id, count(*)::int as n
        from public.journey_events
        where hospital_id = p_hospital_id and type = 'wrong_turn' and occurred_at >= v_day
        group by 1 order by 2 desc limit 5
      ) w), '[]'::jsonb)
  );
end $$;

revoke execute on function public.get_flow(text, int) from anon, public;
grant execute on function public.get_flow(text, int) to authenticated;

-- One broadcast per insert batch (not per row), so a busy simulator does not flood the dashboards.
create or replace function public.broadcast_flow() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  h text;
begin
  for h in select distinct hospital_id from new_rows loop
    perform realtime.send(jsonb_build_object('hospital_id', h), 'flow_changed', 'hospital:' || h, false);
  end loop;
  return null;
end $$;

create trigger journey_events_broadcast after insert on public.journey_events
  referencing new table as new_rows
  for each statement execute function public.broadcast_flow();
