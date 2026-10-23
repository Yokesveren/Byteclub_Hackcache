export type Band = "green" | "yellow" | "red";
export type DoctorStatus = "in" | "break" | "away" | "delayed" | "off";
export type StaffRole = "counter" | "triage" | "doctor" | "admin" | "demo";
export type Level = "green" | "amber" | "red";

export interface Staff {
  user_id: string;
  name: string;
  role: StaffRole;
  hospital_id: string | null;
  doctor_id: string | null;
}

export interface UrgentFlag {
  id: string;
  source: string;
  message: string | null;
  raised_at: string;
  received_at: string;
  waypoint_id: string | null;
  session_id: string | null;
  visit_id: string | null;
  token: string | null;
  first_name: string | null;
  band: Band | null;
  doctor_name: string | null;
  room: string | null;
  floor: number | null;
  needs_stair_free: boolean | null;
}

export interface QueueItem {
  visit_id: string;
  position: number;
  token: string;
  first_name: string;
  age_group: string;
  age_years: number | null;
  special_needs: string[];
  language: string;
  band: Band;
  provisional: boolean;
  band_source: string;
  channel: string;
  arrived_at: string;
  waited_min: number;
  needs_stair_free: boolean;
  needs_visual_call: boolean;
  no_show_count: number;
  doctor_note: string | null;
  has_flag: boolean;
}

export interface Serving {
  visit_id: string;
  token: string;
  first_name: string;
  band: Band;
  called_at: string;
  call_count: number;
  needs_visual_call: boolean;
  doctor_note: string | null;
}

export interface Doctor {
  id: string;
  name: string;
  room: string;
  floor: number;
  department_id: string;
  status: DoctorStatus;
  status_note: string | null;
  status_changed_at: string;
  consult_min: number;
  seen_today: number;
  serving: Serving | null;
  queue: QueueItem[];
}

export interface Department {
  id: string;
  code: string;
  name: string;
  waiting: number;
  amber_at: number;
  red_at: number;
  level: Level;
}

export interface Hospital {
  id: string;
  name: string;
  short_name: string;
  district: string;
  address: string;
  lat: number;
  lng: number;
  services: Record<string, string>;
}

export interface FairnessAlert {
  visit_id: string;
  token: string;
  doctor_id: string;
  waited_min: number;
  overtaken: number;
}

export interface Facility {
  now: string;
  hospital: Hospital;
  urgent_flags: UrgentFlag[];
  departments: Department[];
  doctors: Doctor[];
  unassigned: { visit_id: string; token: string; department_id: string }[];
  fairness: FairnessAlert[];
  stats: {
    waiting: number;
    seen: number;
    unconfirmed: number;
    flags_today: number;
    median_ack_sec: number | null;
  };
}

export interface MyTurn {
  visit_id: string;
  hospital: { id: string; name: string; address: string; lat: number; lng: number };
  token: string;
  offline_token: string | null;
  first_name: string;
  department: { id: string; code: string; name: string; name_i18n: Record<string, string> };
  doctor: {
    id: string;
    name: string;
    room: string;
    floor: number;
    room_waypoint_id: string | null;
    stair_free: boolean;
    status: DoctorStatus;
    status_note: string | null;
  } | null;
  band: Band;
  band_label: string;
  band_provisional: boolean;
  status: string;
  now_serving: string | null;
  estimate: {
    people_ahead: number;
    estimate_min: number;
    window_start: string;
    window_end: string;
    paused: boolean;
    consult_min: number;
  } | null;
  last_reason: { type: string; reason_code: string; message: string; at: string } | null;
  slip_qr: string;
  last_updated: string;
}

export interface BoardDoctor {
  doctor_id: string;
  doctor_name: string;
  room: string;
  floor: number;
  status: DoctorStatus;
  department: string;
  serving: { token: string; first_name: string; called_at: string; visual_call: boolean; call_count: number } | null;
  next: string[];
}

export interface Board {
  hospital: { id: string; name: string };
  now: string;
  doctors: BoardDoctor[];
}

export interface LogEntry {
  id: string;
  created_at: string;
  type: string;
  actor: string;
  actor_role: string;
  reason_code: string | null;
  note: string | null;
  from_value: Record<string, unknown> | null;
  to_value: Record<string, unknown> | null;
  patient_message: string | null;
  doctor_id: string | null;
  doctor_name: string | null;
  token: string | null;
}
