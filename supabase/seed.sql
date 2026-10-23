-- SevaSetu demo seed. Every hospital, doctor and patient here is made up (PRD 3.2, S9).
-- Translations in name_i18n need native-speaker review before any pilot (PRD 16.1).

insert into public.hospitals (id, name, short_name, district, address, lat, lng, phone, services) values
  ('H01', 'Chinar District Hospital (Demo)', 'Chinar DH', 'Anantnag', 'Hospital Road, Anantnag (fictional)', 33.7311, 75.1487, '01932-000000',
   '{"lab":"up","xray":"up","pharmacy":"up"}'),
  ('H02', 'Jhelum Sub-District Hospital (Demo)', 'Jhelum SDH', 'Anantnag', 'Main Bazaar, Bijbehara (fictional)', 33.7928, 75.1070, '01932-000001',
   '{"lab":"up","xray":"down","pharmacy":"up"}'),
  ('H03', 'Lidder Community Health Centre (Demo)', 'Lidder CHC', 'Anantnag', 'Pahalgam Road, Seer (fictional)', 33.8350, 75.2410, '01936-000002',
   '{"lab":"up","xray":"down","pharmacy":"low"}');

insert into public.departments (id, hospital_id, code, name, name_i18n, amber_at, red_at, default_consult_min) values
  ('H01-GM', 'H01', 'M', 'General Medicine', '{"en":"General Medicine","hi":"सामान्य चिकित्सा","ur":"جنرل میڈیسن"}', 15, 25, 6),
  ('H01-PD', 'H01', 'P', 'Paediatrics', '{"en":"Paediatrics","hi":"बाल रोग","ur":"بچوں کا شعبہ"}', 8, 14, 7),
  ('H01-MH', 'H01', 'A', 'Maternal Health', '{"en":"Maternal Health","hi":"मातृ स्वास्थ्य","ur":"زچہ و بچہ"}', 8, 14, 8),
  ('H01-OR', 'H01', 'O', 'Orthopaedics', '{"en":"Orthopaedics","hi":"हड्डी रोग","ur":"ہڈیوں کا شعبہ"}', 8, 14, 7),
  ('H02-GM', 'H02', 'M', 'General Medicine', '{"en":"General Medicine","hi":"सामान्य चिकित्सा","ur":"جنرل میڈیسن"}', 10, 18, 6),
  ('H02-PD', 'H02', 'P', 'Paediatrics', '{"en":"Paediatrics","hi":"बाल रोग","ur":"بچوں کا شعبہ"}', 6, 10, 7),
  ('H02-MH', 'H02', 'A', 'Maternal Health', '{"en":"Maternal Health","hi":"मातृ स्वास्थ्य","ur":"زچہ و بچہ"}', 6, 10, 8),
  ('H03-GM', 'H03', 'M', 'General Medicine', '{"en":"General Medicine","hi":"सामान्य चिकित्सा","ur":"جنرل میڈیسن"}', 8, 14, 6),
  ('H03-PD', 'H03', 'P', 'Paediatrics', '{"en":"Paediatrics","hi":"बाल रोग","ur":"بچوں کا شعبہ"}', 5, 9, 7);

-- floor 0 = ground floor, 1 = first floor. room_waypoint_id matches the hospital graph.
insert into public.doctors (id, hospital_id, department_id, name, room, floor, room_waypoint_id, stair_free, default_consult_min) values
  ('H01-D01', 'H01', 'H01-GM', 'Dr. Aamir Lone', 'Room 3', 1, 'SS:H01:F1:W103', true, 5.5),
  ('H01-D02', 'H01', 'H01-GM', 'Dr. Sadia Bhat', 'Room 4', 1, 'SS:H01:F1:W104', true, 6.5),
  ('H01-D03', 'H01', 'H01-GM', 'Dr. Vivek Sharma', 'Room 5', 1, 'SS:H01:F1:W105', true, 6),
  ('H01-D04', 'H01', 'H01-PD', 'Dr. Nazia Rather', 'Room 7', 0, 'SS:H01:F0:W007', true, 7),
  ('H01-D05', 'H01', 'H01-PD', 'Dr. Anil Gupta', 'Room 8', 0, 'SS:H01:F0:W008', true, 7.5),
  ('H01-D06', 'H01', 'H01-MH', 'Dr. Farhana Shah', 'Room 10', 0, 'SS:H01:F0:W010', true, 8),
  ('H01-D07', 'H01', 'H01-MH', 'Dr. Meenakshi Raina', 'Room 11', 0, 'SS:H01:F0:W011', true, 8.5),
  ('H01-D08', 'H01', 'H01-OR', 'Dr. Irfan Dar', 'Room 14', 1, 'SS:H01:F1:W114', true, 7),
  ('H01-D09', 'H01', 'H01-OR', 'Dr. Sunil Koul', 'Room 15', 1, 'SS:H01:F1:W115', false, 6.5),
  ('H02-D01', 'H02', 'H02-GM', 'Dr. Showkat Mir', 'Room 1', 0, null, true, 6),
  ('H02-D02', 'H02', 'H02-GM', 'Dr. Ritu Bali', 'Room 2', 0, null, true, 6),
  ('H02-D03', 'H02', 'H02-PD', 'Dr. Asif Khan', 'Room 3', 0, null, true, 7),
  ('H02-D04', 'H02', 'H02-MH', 'Dr. Rukhsana Akhter', 'Room 4', 0, null, true, 8),
  ('H03-D01', 'H03', 'H03-GM', 'Dr. Tanveer Ganie', 'OPD 1', 0, null, true, 6),
  ('H03-D02', 'H03', 'H03-PD', 'Dr. Priya Kotwal', 'OPD 2', 0, null, true, 7);

-- Demo staff logins (password for all: sevasetu-demo). Local development only.
do $$
declare
  u record;
begin
  for u in select * from (values
    ('11111111-1111-1111-1111-111111111101'::uuid, 'triage@sevasetu.demo',  'Nurse Haleema (triage)', 'triage',  'H01', null),
    ('11111111-1111-1111-1111-111111111102'::uuid, 'counter@sevasetu.demo', 'Counter desk 1',         'counter', 'H01', null),
    ('11111111-1111-1111-1111-111111111103'::uuid, 'doctor@sevasetu.demo',  'Dr. Aamir Lone',         'doctor',  'H01', 'H01-D01'),
    ('11111111-1111-1111-1111-111111111104'::uuid, 'admin@sevasetu.demo',   'District Health Officer','admin',   null,  null),
    ('11111111-1111-1111-1111-111111111105'::uuid, 'demo@sevasetu.demo',    'Demo operator',          'demo',    null,  null)
  ) as t(id, email, name, role, hospital_id, doctor_id) loop
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
                            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
                            confirmation_token, email_change, email_change_token_new, recovery_token)
    values ('00000000-0000-0000-0000-000000000000', u.id, 'authenticated', 'authenticated', u.email,
            extensions.crypt('sevasetu-demo', extensions.gen_salt('bf')), now(),
            '{"provider":"email","providers":["email"]}', jsonb_build_object('name', u.name), now(), now(),
            '', '', '', '');
    insert into auth.identities (id, user_id, provider_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
    values (gen_random_uuid(), u.id, u.id::text,
            jsonb_build_object('sub', u.id::text, 'email', u.email, 'email_verified', true),
            'email', now(), now(), now());
    insert into public.staff (user_id, name, role, hospital_id, doctor_id)
    values (u.id, u.name, u.role, u.hospital_id, u.doctor_id);
  end loop;
end $$;

select public.demo_reset();
