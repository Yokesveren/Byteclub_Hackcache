-- ---------------------------------------------------------------------------
-- SMS and missed-call fallback (PRD 7.2 step 3) and the protocol-bound help chatbot (PRD 11.4, S7).
--
-- SMS: a basic phone with no data can text the hospital number. "SLIP" returns the token and place in the queue,
-- "REG 1 Asha" registers, "HELP" raises an urgent flag; a missed call replies with the menu. Patients with a phone
-- number get an SMS when 3 people are ahead, when called, when sent to Emergency, and before a follow-up.
-- There is no SMS provider in the demo (PRD 18.1: simulated): every message is stored in sms_messages with
-- status 'mock'. A real gateway reads status = 'queued' rows and calls sms_receive for incoming texts.
--
-- Chat: answers only from approved content (chat_faq), live hospital facts and the map's place tags. Warning signs
-- stop the chat and show Emergency; diagnosis and medicine questions are refused; anything else goes to the help desk.
-- ---------------------------------------------------------------------------

create table public.sms_messages (
  id uuid primary key default gen_random_uuid(),
  hospital_id text not null references public.hospitals,
  phone text not null,
  direction text not null check (direction in ('in', 'out')),
  kind text not null,             -- in: text, missed_call; out: reply, menu, registered, near_turn, called, emergency, reminder, followup
  body text not null check (char_length(body) <= 480),
  visit_id uuid references public.visits on delete set null,
  status text not null default 'mock' check (status in ('mock', 'queued', 'sent', 'failed', 'received')),
  created_at timestamptz not null default public.app_now()
);
create index sms_messages_phone on public.sms_messages (phone, created_at desc);
create index sms_messages_visit_kind on public.sms_messages (visit_id, kind);
alter table public.sms_messages enable row level security;
create policy "staff read" on public.sms_messages for select to authenticated using (public.staff_can_see(hospital_id));

-- Digits only, with the country code dropped, so "+91 98765 43210" and "9876543210" are the same phone.
create or replace function public.sms_norm(p text) returns text
language sql immutable as $$
  select right(regexp_replace(coalesce(p, ''), '[^0-9]', '', 'g'), 10)
$$;

-- One line in the patient's language (Kashmiri gets Urdu, Dogri Hindi), falling back to English.
create or replace function public.sms_text(p_lang text, p_en text, p_hi text, p_ur text) returns text
language sql immutable as $$
  select case
    when p_lang in ('hi', 'doi') and p_hi is not null then p_hi
    when p_lang in ('ur', 'ks') and p_ur is not null then p_ur
    else p_en end
$$;

create or replace function public.sms_send(p_hospital text, p_phone text, p_kind text, p_body text, p_visit uuid default null)
returns void language sql security definer set search_path = public as $$
  insert into public.sms_messages (hospital_id, phone, direction, kind, body, visit_id)
  values (p_hospital, public.sms_norm(p_phone), 'out', p_kind, left(p_body, 480), p_visit);
$$;

-- "Token M-017 · Dr. Aamir Lone, Room 3 · 5 ahead, likely 11:20-11:50" in the patient's language.
create or replace function public.sms_status_line(p_visit uuid) returns text
language plpgsql stable security definer set search_path = public as $$
declare
  t jsonb := public.get_my_turn(p_visit);
  v_lang text := (select p.language from public.visits v join public.patients p on p.id = v.patient_id where v.id = p_visit);
  v_ahead int := (t->'estimate'->>'people_ahead')::int;
  v_win text := case when t->'estimate'->>'window_start' is not null and not coalesce((t->'estimate'->>'paused')::boolean, false)
                     then to_char((t->'estimate'->>'window_start')::timestamptz at time zone 'Asia/Kolkata', 'HH24:MI') || '-' ||
                          to_char((t->'estimate'->>'window_end')::timestamptz at time zone 'Asia/Kolkata', 'HH24:MI') end;
  v_where text := (t->'doctor'->>'name') || ', ' || (t->'doctor'->>'room');
begin
  if t is null then return null; end if;
  if t->>'status' = 'called' then
    return public.sms_text(v_lang, 'Token ' || (t->>'token') || ': it is your turn. Go to ' || v_where || ' now.',
                                   'टोकन ' || (t->>'token') || ': आपकी बारी है। अभी ' || v_where || ' जाइए।',
                                   'ٹوکن ' || (t->>'token') || ': آپ کی باری ہے۔ ابھی ' || v_where || ' جائیں۔');
  end if;
  if t->>'status' <> 'waiting' then
    return public.sms_text(v_lang, 'Token ' || (t->>'token') || ': ' || (t->>'status') || '.', 'टोकन ' || (t->>'token') || ': ' || (t->>'status') || '।', null);
  end if;
  return public.sms_text(v_lang,
    'Token ' || (t->>'token') || ' · ' || v_where || ' · ' || coalesce(v_ahead, 0) || ' ahead' || coalesce(', likely ' || v_win, '') || '. Feeling worse? Reply HELP or go to Emergency.',
    'टोकन ' || (t->>'token') || ' · ' || v_where || ' · आपसे पहले ' || coalesce(v_ahead, 0) || coalesce(', संभावित ' || v_win, '') || '। तबीयत बिगड़े तो HELP लिखें या इमरजेंसी जाएँ।',
    'ٹوکن ' || (t->>'token') || ' · ' || v_where || ' · آپ سے پہلے ' || coalesce(v_ahead, 0) || coalesce('، متوقع ' || v_win, '') || '۔ طبیعت بگڑے تو HELP لکھیں یا ایمرجنسی جائیں۔');
end $$;

create or replace function public.sms_menu(p_hospital text) returns text
language sql stable security definer set search_path = public as $$
  select 'SevaSetu ' || h.short_name || ': reply SLIP for your token and place, REG <number> <first name> to register ('
         || (select string_agg(n || ' ' || name, ', ' order by n)
               from (select row_number() over (order by case code when 'M' then 1 when 'P' then 2 when 'A' then 3 else 4 end) as n, name
                       from public.departments where hospital_id = h.id) d)
         || '), HELP if you feel worse. Chest pain, breathing trouble, fits or heavy bleeding: go to Emergency now, no token needed.'
  from public.hospitals h where h.id = p_hospital
$$;

-- The patient's latest visit today at this hospital, by phone.
create or replace function public.sms_visit_for(p_hospital text, p_phone text) returns uuid
language sql stable security definer set search_path = public as $$
  select v.id from public.visits v join public.patients p on p.id = v.patient_id
  where v.hospital_id = p_hospital and v.day = public.app_today() and public.sms_norm(p.phone) = public.sms_norm(p_phone)
  order by (v.status in ('waiting', 'called')) desc, v.created_at desc limit 1
$$;

-- An incoming text (from the SMS gateway, or the dashboard's mock phone). Returns the reply that was sent.
create or replace function public.sms_receive(p jsonb) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_hospital text := p->>'hospital_id';
  v_phone text := public.sms_norm(p->>'from');
  v_body text := trim(coalesce(p->>'body', ''));
  v_words text[] := regexp_split_to_array(upper(v_body), '\s+');
  v_cmd text := v_words[1];
  v_visit uuid;
  v_reply text;
  v_kind text := 'reply';
  v_dept record;
  v_name text;
  v_turn jsonb;
begin
  if not exists (select 1 from public.hospitals where id = v_hospital) then raise exception 'unknown hospital'; end if;
  if char_length(v_phone) < 6 then raise exception 'a phone number is required'; end if;
  insert into public.sms_messages (hospital_id, phone, direction, kind, body, status)
  values (v_hospital, v_phone, 'in', 'text', left(v_body, 480), 'received');

  if v_cmd in ('SLIP', 'STATUS', 'TOKEN', 'PARCHI', 'पर्ची') then
    if array_length(v_words, 1) >= 2 then
      select v.id into v_visit from public.visits v
       where v.hospital_id = v_hospital and v.day = public.app_today() and upper(v.token) = upper(v_words[2]) limit 1;
    else
      v_visit := public.sms_visit_for(v_hospital, v_phone);
    end if;
    v_reply := coalesce(public.sms_status_line(v_visit),
      'No token found for this phone today. Reply REG <number> <first name> to register, or ask at the counter.');

  elsif v_cmd in ('REG', 'REGISTER') then
    -- REG <department number or code> <first name> [age]
    select * into v_dept from (
      select d.*, row_number() over (order by case d.code when 'M' then 1 when 'P' then 2 when 'A' then 3 else 4 end) as n
      from public.departments d where d.hospital_id = v_hospital) x
    where x.n::text = v_words[2] or x.code = v_words[2] limit 1;
    v_name := initcap(lower(nullif(array_to_string(v_words[3:3], ' '), '')));
    if v_dept.id is null or v_name is null then
      v_reply := public.sms_menu(v_hospital);
      v_kind := 'menu';
    else
      v_visit := public.sms_visit_for(v_hospital, v_phone);
      if v_visit is not null and (select status from public.visits where id = v_visit) = 'waiting' then
        v_reply := 'You already have a token today. ' || public.sms_status_line(v_visit);
      else
        v_visit := gen_random_uuid();
        perform public.register_visit(jsonb_build_object(
          'visit_id', v_visit, 'hospital_id', v_hospital, 'department_code', v_dept.code, 'channel', 'sms',
          'suggested_band', 'green', 'doctor_note', 'Registered by SMS; no symptom check done. Please check warning signs at the counter.',
          'patient', jsonb_build_object(
            'id', gen_random_uuid(), 'first_name', v_name, 'phone', v_phone,
            -- Reply in the script the patient wrote in: Devanagari gives Hindi, Arabic script Urdu.
            'language', case when v_body ~ U&'[\0900-\097F]' then 'hi' when v_body ~ U&'[\0600-\06FF]' then 'ur' else 'en' end,
            'age_group', case when v_words[4] ~ '^\d+$' then
                            case when v_words[4]::int < 1 then 'infant' when v_words[4]::int < 18 then 'child'
                                 when v_words[4]::int < 60 then 'adult' else 'elderly' end
                          when v_dept.code = 'P' then 'child' else 'adult' end,
            'age_years', case when v_words[4] ~ '^\d+$' then v_words[4]::int end,
            'consent', jsonb_build_object('terms', true, 'movement', false, 'via', 'sms'))));
        v_reply := 'Registered. ' || public.sms_status_line(v_visit) || ' Show this SMS at the counter.';
        v_kind := 'registered';
      end if;
    end if;

  elsif v_cmd in ('HELP', 'URGENT', 'MADAD', 'मदद', 'مدد') then
    v_visit := public.sms_visit_for(v_hospital, v_phone);
    perform public.raise_urgent_flag(jsonb_build_object(
      'id', gen_random_uuid(), 'hospital_id', v_hospital, 'visit_id', v_visit,
      'session_id', case when v_visit is null then 'sms-' || v_phone end,
      'source', 'sms', 'message', 'Urgent help by SMS from ' || right(v_phone, 4) || coalesce(': ' || nullif(trim(substr(v_body, char_length(split_part(v_body, ' ', 1)) + 1)), ''), ''),
      'raised_at', public.app_now()));
    v_reply := 'Sent to staff. Please also tell any staff member now. If you have chest pain, breathing trouble or bleeding, go to Emergency (Casualty).';
    v_kind := 'urgent';

  else
    v_reply := public.sms_menu(v_hospital);
    v_kind := 'menu';
  end if;

  perform public.sms_send(v_hospital, v_phone, v_kind, v_reply, v_visit);
  return jsonb_build_object('reply', v_reply, 'kind', v_kind, 'visit_id', v_visit);
end $$;

-- A missed call to the hospital number: logged, answered with the SMS menu (PRD 7.2: "creates an SMS registration").
create or replace function public.sms_missed_call(p jsonb) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_hospital text := p->>'hospital_id';
  v_phone text := public.sms_norm(p->>'from');
  v_visit uuid;
  v_reply text;
begin
  if not exists (select 1 from public.hospitals where id = v_hospital) then raise exception 'unknown hospital'; end if;
  if char_length(v_phone) < 6 then raise exception 'a phone number is required'; end if;
  insert into public.sms_messages (hospital_id, phone, direction, kind, body, status)
  values (v_hospital, v_phone, 'in', 'missed_call', 'Missed call', 'received');
  v_visit := public.sms_visit_for(v_hospital, v_phone);
  v_reply := coalesce(public.sms_status_line(v_visit), public.sms_menu(v_hospital));
  perform public.sms_send(v_hospital, v_phone, case when v_visit is null then 'menu' else 'reply' end, v_reply, v_visit);
  return jsonb_build_object('reply', v_reply, 'visit_id', v_visit);
end $$;

-- Automatic texts for patients with a phone number: near turn (3 or fewer ahead), called, sent to Emergency.
-- Each is sent once per visit.
create or replace function public.sms_queue_alerts() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  r record;
begin
  for r in
    select v.id, v.hospital_id, v.status, p.phone, p.language
    from public.visits v join public.patients p on p.id = v.patient_id
    where v.day = public.app_today() and p.phone is not null and v.status in ('called', 'emergency')
      and not exists (select 1 from public.sms_messages m where m.visit_id = v.id and m.kind = v.status)
  loop
    perform public.sms_send(r.hospital_id, r.phone, r.status,
      case when r.status = 'called' then public.sms_status_line(r.id)
           else public.sms_text(r.language, 'Please go to Emergency (Casualty) now. You do not need a token.',
                                'कृपया अभी इमरजेंसी (कैजुअल्टी) जाइए। टोकन की ज़रूरत नहीं।', 'براہ کرم ابھی ایمرجنسی (کیجولٹی) جائیں۔ ٹوکن کی ضرورت نہیں۔') end,
      r.id);
  end loop;
  for r in
    select q.id, q.hospital_id, p.phone
    from public.queue_positions q join public.patients p on p.id = q.patient_id
    where p.phone is not null and q.position - 1 <= 3
      and not exists (select 1 from public.sms_messages m where m.visit_id = q.id and m.kind = 'near_turn')
  loop
    perform public.sms_send(r.hospital_id, r.phone, 'near_turn', public.sms_status_line(r.id), r.id);
  end loop;
  return null;
end $$;

create trigger sms_queue_alerts after insert or update of status, doctor_id, confirmed_band on public.visits
  for each statement execute function public.sms_queue_alerts();

-- Follow-up reminders (PRD 11.2): 2 days before and the day before, once each.
create or replace function public.sms_followup_reminders() returns int
language plpgsql security definer set search_path = public as $$
declare
  r record;
  n int := 0;
begin
  for r in
    select j.id, j.visit_id, j.hospital_id, j.follow_up_on, p.phone, p.language, p.first_name, h.short_name,
           (j.follow_up_on - public.app_today()) as days
    from public.health_journey j
    join public.patients p on p.id = j.patient_id
    join public.hospitals h on h.id = j.hospital_id
    where j.follow_up_on - public.app_today() in (1, 2) and p.phone is not null
      and not exists (select 1 from public.sms_messages m
                       where m.visit_id = j.visit_id and m.kind = 'reminder' and m.body like '%' || to_char(j.follow_up_on, 'DD Mon') || '%'
                         and m.created_at::date = public.app_today())
  loop
    perform public.sms_send(r.hospital_id, r.phone, 'reminder', public.sms_text(r.language,
      r.first_name || ', your follow-up at ' || r.short_name || ' is on ' || to_char(r.follow_up_on, 'DD Mon') || ' (in ' || r.days || ' day' || case when r.days > 1 then 's' else '' end || '). Bring your previous prescription. OPD opens at 09:00.',
      r.first_name || ', ' || r.short_name || ' में आपका दोबारा दिखाना ' || to_char(r.follow_up_on, 'DD Mon') || ' को है। पिछली पर्ची साथ लाइए। ओपीडी 09:00 बजे खुलती है।',
      r.first_name || '، ' || r.short_name || ' میں آپ کا دوبارہ معائنہ ' || to_char(r.follow_up_on, 'DD Mon') || ' کو ہے۔ پچھلا نسخہ ساتھ لائیں۔ او پی ڈی 09:00 بجے کھلتی ہے۔'),
      r.visit_id);
    n := n + 1;
  end loop;
  return n;
end $$;

-- A staff "SMS" follow-up action now really sends the message (to the mock gateway).
create or replace function public.followup_action(p jsonb) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  s public.staff := public.current_staff();
  j public.health_journey;
  v_phone text;
  v_lang text;
  v_name text;
begin
  if s.user_id is null then raise exception 'staff login required'; end if;
  select * into j from public.health_journey where id = (p->>'journey_id')::uuid;
  if j.id is null then raise exception 'follow-up not found'; end if;
  if not public.staff_can_see(j.hospital_id) then raise exception 'not your hospital'; end if;
  insert into public.followup_actions (journey_id, action, note, actor)
  values (j.id, p->>'action', left(p->>'note', 280), s.name);
  if p->>'action' = 'sms' then
    select phone, language, first_name into v_phone, v_lang, v_name from public.patients where id = j.patient_id;
    if v_phone is not null then
      perform public.sms_send(j.hospital_id, v_phone, 'followup', public.sms_text(v_lang,
        v_name || ', you missed your follow-up on ' || to_char(j.follow_up_on, 'DD Mon') || '. Please come to the OPD soon, or reply SLIP after registering. Feeling worse? Go to Emergency.',
        v_name || ', आप ' || to_char(j.follow_up_on, 'DD Mon') || ' को दोबारा दिखाने नहीं आए। कृपया जल्दी ओपीडी आइए। तबीयत बिगड़े तो इमरजेंसी जाइए।',
        v_name || '، آپ ' || to_char(j.follow_up_on, 'DD Mon') || ' کو دوبارہ معائنے کے لیے نہیں آئے۔ براہ کرم جلد او پی ڈی آئیں۔'),
        j.visit_id);
    end if;
  end if;
  return jsonb_build_object('ok', true, 'sms_sent', p->>'action' = 'sms' and v_phone is not null);
end $$;

-- Dashboard: SMS threads for a hospital (staff) or one phone.
create or replace function public.get_sms(p_hospital text, p_phone text default null, p_limit int default 200) returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.staff_can_see(p_hospital) then raise exception 'not your hospital'; end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object('id', m.id, 'phone', m.phone, 'direction', m.direction, 'kind', m.kind, 'body', m.body,
                                        'status', m.status, 'created_at', m.created_at,
                                        'token', (select token from public.visits where id = m.visit_id)) order by m.created_at desc)
    from (select * from public.sms_messages
           where hospital_id = p_hospital and (p_phone is null or phone = public.sms_norm(p_phone))
           order by created_at desc limit p_limit) m), '[]'::jsonb);
end $$;

-- Patient app: the texts this visit's phone number received (whoever holds the visit id, as with get_my_turn).
create or replace function public.get_my_sms(p_visit_id uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('direction', m.direction, 'kind', m.kind, 'body', m.body, 'created_at', m.created_at)
                            order by m.created_at desc), '[]'::jsonb)
  from public.visits v join public.patients p on p.id = v.patient_id
  join public.sms_messages m on m.phone = public.sms_norm(p.phone) and m.hospital_id = v.hospital_id
  where v.id = p_visit_id and p.phone is not null and m.created_at::date >= v.day - 7
$$;

grant execute on function public.sms_receive(jsonb), public.sms_missed_call(jsonb), public.get_sms(text, text, int),
  public.sms_followup_reminders() to authenticated;
revoke execute on function public.sms_receive(jsonb), public.sms_missed_call(jsonb), public.get_sms(text, text, int),
  public.sms_followup_reminders(), public.sms_send(text, text, text, text, uuid), public.sms_queue_alerts() from anon, public;
grant execute on function public.get_my_sms(uuid) to anon, authenticated;

-- ---------------------------------------------------------------------------
-- Help chatbot (PRD 11.4, S7)
-- ---------------------------------------------------------------------------

create table public.chat_faq (
  id text primary key,
  hospital_id text references public.hospitals,   -- null: every hospital
  keywords text[] not null,                         -- lower case; English, Hindi, Urdu and romanised Hindi/Urdu
  answer jsonb not null,                            -- {en, hi, ur}
  action jsonb,                                     -- {"type":"place","tag":"pharmacy"} or {"type":"screen","screen":"register"}
  approved_by text not null default 'demo content: needs hospital approval',
  updated_at timestamptz not null default now()
);
alter table public.chat_faq enable row level security;
create policy "public read" on public.chat_faq for select using (true);

create table public.chat_log (
  id uuid primary key default gen_random_uuid(),
  session_id text,
  hospital_id text references public.hospitals,
  language text,
  question text not null check (char_length(question) <= 500),
  outcome text not null,           -- emergency, refused, answer, live, place, help_desk
  faq_id text,
  created_at timestamptz not null default public.app_now()
);
alter table public.chat_log enable row level security;
create policy "staff read" on public.chat_log for select to authenticated using (hospital_id is null or public.staff_can_see(hospital_id));

-- Words that stop the chat and show Emergency (S7). Deliberately broad: a false alarm costs a tap, a miss costs more.
create or replace function public.chat_warning(p text) returns boolean
language sql immutable as $$
  select p ~* '(chest pain|chest tight|heart attack|can''?t breathe|cannot breathe|breathless|short(ness)? of breath|breathing (problem|trouble|difficult)|unconscious|not responding|faint|collapse|fits?\y|seizure|convulsion|bleeding|blood loss|vomiting blood|stroke|face droop|slurred|paralys|poison|snake ?bite|suicid|kill myself|severe pain|burn|accident|baby (is )?(blue|not feeding)|pregnan.{0,20}(bleed|pain)|seene? (me|mein|men) dard|saa?ns (nahi|lene|phool)|behosh|daura|khoon|lakwa|zeh?er|saa?np|सीने|छाती|सांस|साँस|बेहोश|दौरा|खून|लकवा|ज़हर|जहर|सांप|साँप|سینے|سانس|بے ہوش|بےہوش|دورہ|خون|فالج|زہر|سانپ)'
$$;

-- Diagnosis, medicines, doses and reports are refused (S7).
create or replace function public.chat_refused(p text) returns boolean
language sql immutable as $$
  select p ~* '(\ydose|dosage|\ymg\y|tablet|capsule|syrup|injection|medicine|antibiotic|paracetamol|which drug|what disease|do i have|diagnos|is it (cancer|serious|covid)|report (says|mean)|test result|my report|dawai|dawa\y|goli|bimari|kya hua hai|दवा|दवाई|गोली|बीमारी|रिपोर्ट|دوا|گولی|بیماری|رپورٹ)'
$$;

create or replace function public.chat_ask(p jsonb) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_hospital text := coalesce(p->>'hospital_id', 'H01');
  v_lang text := coalesce(p->>'lang', 'en');
  v_base text := case when v_lang in ('hi', 'doi') then 'hi' when v_lang in ('ur', 'ks') then 'ur' else 'en' end;
  v_q text := left(trim(coalesce(p->>'text', '')), 500);
  v_low text := lower(v_q);
  v_visit uuid := (p->>'visit_id')::uuid;
  v_out jsonb;
  v_outcome text;
  v_faq text;
  f record;
  h public.hospitals;
  t jsonb;
  v_docs text;
  v_tag text;
begin
  if v_q = '' then raise exception 'empty question'; end if;
  select * into h from public.hospitals where id = v_hospital;
  if h.id is null then raise exception 'unknown hospital'; end if;

  if public.chat_warning(v_low) then
    v_outcome := 'emergency';
    v_out := jsonb_build_object('type', 'emergency', 'text', public.sms_text(v_base,
      'This sounds urgent. Please go to Emergency (Casualty) now; you do not need a token. Or press "I need urgent help".',
      'यह गंभीर लग रहा है। कृपया अभी इमरजेंसी (कैजुअल्टी) जाइए; टोकन की ज़रूरत नहीं। या "मुझे तुरंत मदद चाहिए" दबाइए।',
      'یہ سنگین لگ رہا ہے۔ براہ کرم ابھی ایمرجنسی (کیجولٹی) جائیں؛ ٹوکن کی ضرورت نہیں۔ یا "مجھے فوری مدد چاہیے" دبائیں۔'));

  elsif public.chat_refused(v_low) then
    v_outcome := 'refused';
    v_out := jsonb_build_object('type', 'refuse', 'text', public.sms_text(v_base,
      'I can''t advise on illnesses, medicines, doses or reports. The doctor will. If you feel worse, press Emergency.',
      'मैं बीमारी, दवा, खुराक या रिपोर्ट के बारे में सलाह नहीं दे सकता। यह डॉक्टर बताएँगे। तबीयत बिगड़े तो इमरजेंसी दबाइए।',
      'میں بیماری، دوا، خوراک یا رپورٹ کے بارے میں مشورہ نہیں دے سکتا۔ یہ ڈاکٹر بتائیں گے۔ طبیعت بگڑے تو ایمرجنسی دبائیں۔'),
      'action', jsonb_build_object('type', 'place', 'tag', 'help'));

  elsif v_visit is not null and v_low ~* '(my turn|my token|how long|how many (people|ahead)|when will|meri baa?ri|kitne log|kab tak|मेरी बारी|कितने लोग|कब तक|میری باری|کتنے لوگ|کب تک)' then
    t := public.get_my_turn(v_visit);
    v_outcome := 'live';
    v_out := jsonb_build_object('type', 'answer', 'text', coalesce(public.sms_status_line(v_visit), 'I could not find your token.'),
                                'action', jsonb_build_object('type', 'screen', 'screen', 'myturn'));

  elsif v_low ~* '(doctor|daktar|dr\.?\s|डॉक्टर|ڈاکٹر).{0,30}(in|here|available|aaye|aaya|hai|है|ہیں|ہے|present)|is the doctor|kaun sa doctor' then
    select string_agg(d.name || ' (' || d.room || '): ' || case d.status when 'in' then 'in' when 'break' then 'on a break' when 'delayed' then 'delayed' when 'off' then 'off today' else 'away' end, '; ' order by d.id)
      into v_docs from public.doctors d where d.hospital_id = v_hospital;
    v_outcome := 'live';
    v_out := jsonb_build_object('type', 'answer', 'text', public.sms_text(v_base, 'Doctors now: ' || v_docs || '.', 'अभी डॉक्टर: ' || v_docs || '।', 'ابھی ڈاکٹر: ' || v_docs || '۔'));

  elsif v_low ~* '(timing|time|open|close|hours|kab khul|kab band|samay|waqt|समय|कब खुल|وقت|کب کھل)' and v_low !~* '(turn|baari|बारी|باری)' then
    v_outcome := 'live';
    v_out := jsonb_build_object('type', 'answer', 'text', public.sms_text(v_base,
      'OPD at ' || h.short_name || ' is open ' || h.opd_hours || ', Monday to Saturday. Emergency is open day and night.',
      h.short_name || ' में ओपीडी ' || h.opd_hours || ' खुली रहती है, सोमवार से शनिवार। इमरजेंसी दिन-रात खुली है।',
      h.short_name || ' میں او پی ڈی ' || h.opd_hours || ' کھلی رہتی ہے، پیر سے ہفتہ۔ ایمرجنسی دن رات کھلی ہے۔'));

  elsif v_low ~* '(x-?ray|lab|test|pharmacy|chemist|ultrasound|ecg).{0,25}(work|working|open|chal|chalu|band|down|available)' then
    v_outcome := 'live';
    v_out := jsonb_build_object('type', 'answer', 'text', (
      select 'Today at ' || h.short_name || ': ' || string_agg(
        case key when 'xray' then 'X-ray' when 'lab' then 'Laboratory' when 'pharmacy' then 'Pharmacy' else initcap(key) end
        || ' ' || case when value #>> '{}' = 'up' then 'working' else 'not working' end, ', ') || '.'
      from jsonb_each(h.services)));

  else
    -- A place? "where is the pharmacy" → the map's tag, so the app can show the way.
    v_tag := case
      when v_low ~* '(pharmac|chemist|medicine window|dawa ?khana|दवाखाना|फार्मेसी|فارمیسی|دوا خانہ)' then 'pharmacy'
      when v_low ~* '(\ylab\y|laborator|sample|blood test|जाँच|प्रयोगशाला|لیب|لیبارٹری)' then 'lab'
      when v_low ~* '(x-?ray|एक्स-?रे|ایکس رے)' then 'xray'
      when v_low ~* '(toilet|washroom|bathroom|shauchalay|शौचालय|ٹوائلٹ|بیت الخلا)' then 'toilet'
      when v_low ~* '(registration|counter|parchi|पर्ची|पंजीकरण|رجسٹریشن|کاؤنٹر)' then 'registration'
      when v_low ~* '(help desk|enquiry|inquiry|सहायता|ہیلپ ڈیسک)' then 'help'
      when v_low ~* '(waiting|baith|बैठ|انتظار)' then 'waiting'
      when v_low ~* '(lift|elevator|लिफ्ट|لفٹ)' then 'lift'
      when v_low ~* '(emergency|casualty|इमरजेंसी|ایمرجنسی)' then 'emergency'
    end;
    if v_tag is not null and v_low ~* '(where|kahan|kidhar|how (do i|to) (get|go|reach)|way to|कहाँ|कहां|किधर|کہاں|کدھر)' then
      v_outcome := 'place';
      v_out := jsonb_build_object('type', 'answer', 'text', public.sms_text(v_base,
        'I can show you the way. Tap "Show me the way" and scan the nearest sticker on a wall.',
        'मैं रास्ता दिखा सकता हूँ। "रास्ता दिखाएँ" दबाइए और दीवार पर सबसे पास का स्टिकर स्कैन कीजिए।',
        'میں راستہ دکھا سکتا ہوں۔ "راستہ دکھائیں" دبائیں اور دیوار پر سب سے قریبی اسٹیکر اسکین کریں۔'),
        'action', jsonb_build_object('type', 'place', 'tag', v_tag));
    else
      -- Approved FAQ: the entry sharing the most keywords with the question.
      select c.id, c.answer, c.action, (select count(*) from unnest(c.keywords) k where v_low like '%' || k || '%') as score
        into f
        from public.chat_faq c
       where c.hospital_id is null or c.hospital_id = v_hospital
       order by score desc, (c.hospital_id is not null) desc
       limit 1;
      if f.score > 0 then
        v_outcome := 'answer'; v_faq := f.id;
        v_out := jsonb_build_object('type', 'answer', 'text', coalesce(f.answer->>v_base, f.answer->>'en'), 'faq_id', f.id, 'action', f.action);
      else
        v_outcome := 'help_desk';
        v_out := jsonb_build_object('type', 'help_desk', 'text', public.sms_text(v_base,
          'I can''t help with that. Please ask a staff member at the help desk.',
          'मैं इसमें मदद नहीं कर सकता। कृपया सहायता डेस्क पर किसी स्टाफ़ से पूछिए।',
          'میں اس میں مدد نہیں کر سکتا۔ براہ کرم ہیلپ ڈیسک پر کسی اسٹاف سے پوچھیں۔'),
          'action', jsonb_build_object('type', 'place', 'tag', 'help'));
      end if;
    end if;
  end if;

  insert into public.chat_log (session_id, hospital_id, language, question, outcome, faq_id)
  values (left(p->>'session_id', 64), v_hospital, v_lang, v_q, v_outcome, v_faq);
  return v_out || jsonb_build_object('outcome', v_outcome);
end $$;

create or replace function public.get_chat_log(p_hospital text, p_limit int default 100) returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.staff_can_see(p_hospital) then raise exception 'not your hospital'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object('question', question, 'outcome', outcome, 'faq_id', faq_id,
                                                       'language', language, 'created_at', created_at) order by created_at desc)
                   from (select * from public.chat_log where hospital_id = p_hospital order by created_at desc limit p_limit) l), '[]'::jsonb);
end $$;

grant execute on function public.chat_ask(jsonb) to anon, authenticated;
grant execute on function public.get_chat_log(text, int) to authenticated;
revoke execute on function public.get_chat_log(text, int) from anon, public;

-- Approved answers (demo content; a hospital must approve each before real use).
insert into public.chat_faq (id, keywords, answer, action) values
('bring', array['bring','carry','document','papers','id proof','aadhaar','abha','kya laye','kya lana','साथ','लाना','लाएँ','कागज','لانا','کاغذ'],
 '{"en":"Bring any old prescriptions and test reports, your OPD slip if you have one, and an ID if you can. ABHA is optional. No Aadhaar is needed to register on SevaSetu.","hi":"पुरानी पर्चियाँ और जाँच रिपोर्ट, ओपीडी पर्ची (अगर है) और हो सके तो कोई पहचान पत्र लाइए। आभा वैकल्पिक है। सेवासेतु पर पंजीकरण के लिए आधार नहीं चाहिए।","ur":"پرانے نسخے اور ٹیسٹ رپورٹیں، او پی ڈی پرچی (اگر ہے) اور ہو سکے تو شناختی کارڈ لائیں۔ آبھا اختیاری ہے۔ سیواسیتو پر رجسٹریشن کے لیے آدھار ضروری نہیں۔"}', null),
('token', array['token','queue','line','number','how does','kaise','priority','yellow','green','routine','टोकन','लाइन','ٹوکن','قطار'],
 '{"en":"Your token is your place in your doctor''s queue. Priority (amber) patients, such as babies, the elderly and pregnant women, are seen before Routine (green). If your place changes, the app tells you why.","hi":"टोकन डॉक्टर की लाइन में आपकी जगह है। प्राथमिकता (पीले) वाले मरीज़, जैसे शिशु, बुज़ुर्ग और गर्भवती महिलाएँ, सामान्य (हरे) से पहले देखे जाते हैं। जगह बदले तो ऐप कारण बताता है।","ur":"ٹوکن ڈاکٹر کی قطار میں آپ کی جگہ ہے۔ ترجیحی (پیلے) مریض، جیسے شیر خوار، بزرگ اور حاملہ خواتین، معمول (سبز) سے پہلے دیکھے جاتے ہیں۔ جگہ بدلے تو ایپ وجہ بتاتی ہے۔"}', '{"type":"screen","screen":"myturn"}'),
('register', array['register','registration','new token','get a token','kaise banaye','token kaise','पंजीकरण','टोकन कैसे','رجسٹر','ٹوکن کیسے'],
 '{"en":"Tap Register on the home screen. It asks a few questions, one per screen, checks for warning signs, and gives you a token and a slip with a QR code.","hi":"मुख्य स्क्रीन पर पंजीकरण दबाइए। कुछ सवाल, हर स्क्रीन पर एक, ख़तरे के संकेतों की जाँच, फिर टोकन और QR वाली पर्ची मिलती है।","ur":"ہوم اسکرین پر رجسٹریشن دبائیں۔ چند سوالات، ہر اسکرین پر ایک، خطرے کی علامات کی جانچ، پھر ٹوکن اور QR والی پرچی ملتی ہے۔"}', '{"type":"screen","screen":"register"}'),
('next', array['what happens','after','next','then what','aage kya','phir kya','उसके बाद','आगे क्या','اس کے بعد','آگے کیا'],
 '{"en":"Wait near your doctor''s room or in the waiting area. The app alerts you when 3 people are ahead and when it is your turn. After the doctor, you may go to the pharmacy or the lab, and the app can show you the way.","hi":"अपने डॉक्टर के कमरे के पास या प्रतीक्षा क्षेत्र में रुकिए। जब 3 लोग पहले हों और जब आपकी बारी आए, ऐप बताएगा। डॉक्टर के बाद दवाखाना या लैब जाना हो सकता है, ऐप रास्ता दिखाता है।","ur":"اپنے ڈاکٹر کے کمرے کے پاس یا انتظار گاہ میں رہیں۔ جب 3 لوگ پہلے ہوں اور جب آپ کی باری آئے، ایپ بتائے گی۔ ڈاکٹر کے بعد فارمیسی یا لیب جانا ہو سکتا ہے، ایپ راستہ دکھاتی ہے۔"}', null),
('stickers', array['sticker','qr','scan','map','find the room','lost','raasta','rasta','भटक','रास्ता','راستہ','گم'],
 '{"en":"Every corridor has a QR sticker. Open Find a place, choose where you want to go, and scan the nearest sticker. The app tells you each turn and finds a new route if you take a wrong one.","hi":"हर गलियारे में QR स्टिकर है। जगह खोजें खोलिए, मंज़िल चुनिए और सबसे पास का स्टिकर स्कैन कीजिए। ऐप हर मोड़ बताता है और गलत मुड़ने पर नया रास्ता खोजता है।","ur":"ہر راہداری میں QR اسٹیکر ہے۔ جگہ تلاش کریں کھولیں، منزل چنیں اور سب سے قریبی اسٹیکر اسکین کریں۔ ایپ ہر موڑ بتاتی ہے اور غلط مڑنے پر نیا راستہ تلاش کرتی ہے۔"}', '{"type":"screen","screen":"navigate"}'),
('lost_slip', array['lost slip','lost my slip','paper slip','forgot slip','parchi kho','पर्ची खो','پرچی گم'],
 '{"en":"No problem. Open I have my slip and type your token, or ask at the registration counter with your name and phone number.","hi":"कोई बात नहीं। मेरे पास पर्ची है खोलकर टोकन लिखिए, या नाम और फ़ोन नंबर के साथ पंजीकरण काउंटर पर पूछिए।","ur":"کوئی بات نہیں۔ میرے پاس پرچی ہے کھول کر ٹوکن لکھیں، یا نام اور فون نمبر کے ساتھ رجسٹریشن کاؤنٹر پر پوچھیں۔"}', '{"type":"screen","screen":"haveslip"}'),
('family', array['someone else','my mother','my father','my child','family','for my','dusre','maa','baccha','किसी और','माँ','बच्चे','کسی اور','ماں','بچے'],
 '{"en":"Yes. When you register, choose Someone else. Each person gets their own token.","hi":"हाँ। पंजीकरण में किसी और के लिए चुनिए। हर व्यक्ति को अपना टोकन मिलता है।","ur":"جی ہاں۔ رجسٹریشن میں کسی اور کے لیے چنیں۔ ہر شخص کو اپنا ٹوکن ملتا ہے۔"}', '{"type":"screen","screen":"register"}'),
('wheelchair', array['wheelchair','cannot walk','walk','stretcher','old','elderly','chal nahi','व्हीलचेयर','चल नहीं','وہیل چیئر','چل نہیں'],
 '{"en":"Ask at the help desk for a wheelchair. In Settings, turn on Avoid stairs and the app will route you by lift and ramps.","hi":"व्हीलचेयर के लिए सहायता डेस्क पर पूछिए। सेटिंग्स में सीढ़ियों से बचें चालू करें, ऐप लिफ़्ट और रैंप से रास्ता देगा।","ur":"وہیل چیئر کے لیے ہیلپ ڈیسک پر پوچھیں۔ سیٹنگز میں سیڑھیوں سے بچیں آن کریں، ایپ لفٹ اور ریمپ سے راستہ دے گی۔"}', '{"type":"place","tag":"help"}'),
('no_data', array['no internet','no data','offline','no signal','sms','basic phone','keypad','internet nahi','इंटरनेट नहीं','انٹرنیٹ نہیں'],
 '{"en":"The app works without signal for registration, the slip, maps and Emergency. With a basic phone, send SLIP by SMS to the hospital number for your token and place, or REG to register.","hi":"बिना सिग्नल के भी पंजीकरण, पर्ची, नक्शा और इमरजेंसी चलते हैं। साधारण फ़ोन से अस्पताल के नंबर पर SLIP भेजिए, टोकन और जगह मिलेगी; पंजीकरण के लिए REG।","ur":"بغیر سگنل کے بھی رجسٹریشن، پرچی، نقشہ اور ایمرجنسی چلتے ہیں۔ سادہ فون سے اسپتال کے نمبر پر SLIP بھیجیں، ٹوکن اور جگہ ملے گی؛ رجسٹریشن کے لیے REG۔"}', '{"type":"screen","screen":"sms"}'),
('followup', array['follow up','follow-up','come again','next visit','dobara','दोबारा','अगली बार','دوبارہ'],
 '{"en":"Your follow-up date is in My health journey. You will get a reminder 2 days before and the day before.","hi":"दोबारा आने की तारीख़ मेरी स्वास्थ्य यात्रा में है। 2 दिन पहले और एक दिन पहले याद दिलाया जाएगा।","ur":"دوبارہ آنے کی تاریخ میری صحت کے سفر میں ہے۔ 2 دن پہلے اور ایک دن پہلے یاد دلایا جائے گا۔"}', '{"type":"screen","screen":"journey"}'),
('food', array['food','water','canteen','drink','eat','khana','paani','पानी','खाना','پانی','کھانا'],
 '{"en":"Drinking water is at the water cooler in the main corridor. Ask at the help desk for the canteen.","hi":"पीने का पानी मुख्य गलियारे के कूलर पर है। कैंटीन के लिए सहायता डेस्क पर पूछिए।","ur":"پینے کا پانی مرکزی راہداری کے کولر پر ہے۔ کینٹین کے لیے ہیلپ ڈیسک پر پوچھیں۔"}', '{"type":"place","tag":"help"}'),
('privacy', array['privacy','data','safe','share my','who can see','निजता','جانکاری','رازداری'],
 '{"en":"Your slip shows only your token, first name and department. Scans of wall stickers are shared only if you agree, under a random code. Change this in Settings.","hi":"पर्ची पर सिर्फ़ टोकन, पहला नाम और विभाग होता है। दीवार स्टिकर के स्कैन सिर्फ़ आपकी सहमति से, बेतरतीब कोड के साथ साझा होते हैं। सेटिंग्स में बदल सकते हैं।","ur":"پرچی پر صرف ٹوکن، پہلا نام اور شعبہ ہوتا ہے۔ دیوار اسٹیکر کے اسکین صرف آپ کی رضامندی سے، بے ترتیب کوڈ کے ساتھ شیئر ہوتے ہیں۔ سیٹنگز میں بدل سکتے ہیں۔"}', '{"type":"screen","screen":"settings"}'),
('fee', array['fee','cost','money','pay','charge','paisa','paise','free','फीस','पैसे','فیس','پیسے'],
 '{"en":"SevaSetu itself is free. Ask at the registration counter about any OPD or test fee at this hospital.","hi":"सेवासेतु मुफ़्त है। इस अस्पताल में ओपीडी या जाँच की किसी फ़ीस के बारे में पंजीकरण काउंटर पर पूछिए।","ur":"سیواسیتو مفت ہے۔ اس اسپتال میں او پی ڈی یا ٹیسٹ کی کسی فیس کے بارے میں رجسٹریشن کاؤنٹر پر پوچھیں۔"}', '{"type":"place","tag":"registration"}')
on conflict (id) do nothing;

-- Live updates for the dashboard's Messages screen.
alter publication supabase_realtime add table public.sms_messages, public.chat_log;
