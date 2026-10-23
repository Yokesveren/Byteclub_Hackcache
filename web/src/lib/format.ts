const timeFmt = new Intl.DateTimeFormat("en-IN", {
  timeZone: "Asia/Kolkata",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

const timeSecFmt = new Intl.DateTimeFormat("en-IN", {
  timeZone: "Asia/Kolkata",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false,
});

export function hhmm(ts: string | number | Date | null | undefined): string {
  if (ts == null) return "--:--";
  return timeFmt.format(new Date(ts));
}

export function hhmmss(ts: string | number | Date | null | undefined): string {
  if (ts == null) return "--:--:--";
  return timeSecFmt.format(new Date(ts));
}

export function minutesSince(ts: string, now: number): number {
  return Math.max(0, Math.round((now - new Date(ts).getTime()) / 60000));
}

export function secondsSince(ts: string, now: number): number {
  return Math.max(0, Math.round((now - new Date(ts).getTime()) / 1000));
}

export const floorName = (floor: number) => (floor === 0 ? "Ground floor" : floor === 1 ? "First floor" : `Floor ${floor}`);

export const LANGUAGE_NAMES: Record<string, string> = {
  ks: "Kashmiri",
  ur: "Urdu",
  doi: "Dogri",
  hi: "Hindi",
  en: "English",
};

export const SPECIAL_NEEDS: { id: string; label: string; icon: string }[] = [
  { id: "walking", label: "Walking difficulty", icon: "🦯" },
  { id: "wheelchair", label: "Wheelchair", icon: "♿" },
  { id: "sight", label: "Poor sight", icon: "👓" },
  { id: "hearing", label: "Poor hearing", icon: "🦻" },
  { id: "pregnant", label: "Pregnant", icon: "🤰" },
  { id: "child", label: "With a child", icon: "🧒" },
];

export const DOCTOR_STATUS: Record<string, { label: string; className: string }> = {
  in: { label: "In", className: "bg-emerald-100 text-emerald-900 border-emerald-300" },
  delayed: { label: "Delayed", className: "bg-amber-100 text-amber-900 border-amber-300" },
  break: { label: "On break", className: "bg-sky-100 text-sky-900 border-sky-300" },
  away: { label: "Away", className: "bg-slate-200 text-slate-800 border-slate-300" },
  off: { label: "Off duty", className: "bg-slate-200 text-slate-600 border-slate-300" },
};

export const REASONS: { code: string; label: string }[] = [
  { code: "clinical_review", label: "Clinical review by staff" },
  { code: "priority_patient", label: "Priority patient seen first" },
  { code: "doctor_delayed", label: "Doctor delayed" },
  { code: "doctor_unavailable", label: "Doctor unavailable" },
  { code: "wrong_department", label: "Wrong department" },
  { code: "load_balance", label: "Shorter queue elsewhere" },
  { code: "patient_request", label: "Patient request" },
  { code: "no_show", label: "Did not come when called" },
  { code: "staff_correction", label: "Correction" },
];

export const EVENT_LABELS: Record<string, string> = {
  join: "Joined queue",
  call: "Called",
  recall: "Called again",
  complete: "Consultation done",
  no_show: "No-show, end of queue",
  move_end: "Moved to end",
  move_up: "Moved up",
  move_down: "Moved down",
  set_band: "Band changed",
  confirm_band: "Band confirmed",
  send_to_emergency: "Sent to Emergency",
  move_department: "Moved department",
  assign_doctor: "Doctor changed",
  doctor_status: "Doctor status",
  token_confirmed: "Offline token confirmed",
  priority_join: "Priority patient joined",
};
