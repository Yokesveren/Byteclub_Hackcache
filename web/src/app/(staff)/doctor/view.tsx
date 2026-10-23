"use client";

import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import { BandChip } from "@/components/BandChip";
import { BandFigure, PageBand } from "@/components/PageBand";
import { useShell } from "@/components/StaffShell";
import { DOCTOR_STATUS, floorName, hhmm, LANGUAGE_NAMES, minutesSince, SPECIAL_NEEDS } from "@/lib/format";
import { useLiveData } from "@/lib/hooks";
import { rpc } from "@/lib/supabase";
import type { Band, DoctorStatus } from "@/lib/types";

interface JourneyEntry {
  visit_id: string;
  day: string;
  hospital: string;
  department: string;
  doctor: string | null;
  status: Band;
  follow_up_on: string | null;
  consult_ok: boolean;
  notes: string | null;
}

interface Desk {
  now: string;
  doctor: {
    id: string;
    name: string;
    room: string;
    floor: number;
    status: DoctorStatus;
    status_note: string | null;
    hospital_id: string;
    department: string;
    consult_min: number;
    seen_today: number;
  };
  doctors: { id: string; name: string; room: string }[];
  serving: {
    visit_id: string;
    token: string;
    first_name: string;
    age_group: string;
    age_years: number | null;
    special_needs: string[];
    language: string;
    band: Band;
    provisional: boolean;
    called_at: string;
    doctor_note: string | null;
    journey: JourneyEntry[];
    recorded: { status: Band; follow_up_on: string | null; consult_ok: boolean; notes: string | null } | null;
  } | null;
  queue: {
    visit_id: string;
    position: number;
    token: string;
    first_name: string;
    age_group: string;
    age_years: number | null;
    band: Band;
    provisional: boolean;
    arrived_at: string;
    doctor_note: string | null;
    special_needs: string[];
    history: Band[];
  }[];
  followups: { journey_id: string; first_name: string; status: Band; follow_up_on: string; state: "due" | "defaulted" }[];
}

const STATUS_WORD: Record<Band, string> = { red: "Emergency now", yellow: "Priority", green: "Routine" };
const STATUS_FILL: Record<Band, string> = { red: "#c4242a", yellow: "#ffc013", green: "#2f8a55" };
const FOLLOW_UPS = [
  { days: null, label: "None" },
  { days: 3, label: "3 days" },
  { days: 7, label: "1 week" },
  { days: 14, label: "2 weeks" },
  { days: 30, label: "1 month" },
];
const day = (d: string) => new Date(`${d}T00:00:00`).toLocaleDateString("en-IN", { day: "numeric", month: "short" });

function trend(statuses: Band[]): string | null {
  if (statuses.length < 2) return null;
  const rank = { red: 0, yellow: 1, green: 2 } as const;
  const a = rank[statuses[0]];
  const b = rank[statuses[statuses.length - 1]];
  return b > a ? "Improving" : b < a ? "Getting worse" : "Steady";
}

export default function DoctorView() {
  const { staff, hospitalId, now } = useShell();
  const [picked, setPicked] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const { data, error, refresh } = useLiveData(() => rpc<Desk>("get_doctor_desk", { p_doctor_id: picked }), {
    hospitalId,
    tables: ["visits", "doctors"],
  });

  // The live-data hook reloads on hospital changes; a different doctor needs its own reload.
  useEffect(() => {
    void refresh();
  }, [picked, refresh]);

  const say = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(null), 3500);
  };
  const act = async (fn: string, args: Record<string, unknown>, msg?: string) => {
    try {
      const res = await rpc<{ called?: string | null }>(fn, args);
      if (msg) say(msg);
      if (fn === "staff_action" && (args.p as { action?: string })?.action === "call_next") say(res.called ? `Called ${res.called}` : "No one is waiting");
      await refresh();
    } catch (e) {
      say(e instanceof Error ? e.message : String(e));
    }
  };

  if (!data) return <div className="p-8 text-[var(--muted)]">{error ? `Could not load: ${error}` : "Opening your desk…"}</div>;
  const d = data.doctor;
  const overdue = data.followups.filter((f) => f.state === "defaulted").length;
  const next = data.queue[0];

  return (
    <div>
      <PageBand
        tone="sand"
        title={staff.role === "doctor" ? "My patients" : "Doctor’s desk"}
        lead={
          <>
            <strong>{d.name}</strong> · {d.room}, {floorName(d.floor).toLowerCase()} · {d.department}
          </>
        }
        aside={
          staff.role !== "doctor" && (
            <label className="flex items-center gap-2 font-bold text-[#0a0a0a]">
              Viewing
              <select
                value={picked ?? d.id}
                onChange={(e) => setPicked(e.target.value)}
                className="h-10 rounded-xl bg-white px-3 font-normal"
              >
                {data.doctors.map((x) => (
                  <option key={x.id} value={x.id}>
                    {x.name}, {x.room}
                  </option>
                ))}
              </select>
            </label>
          )
        }
      >
        <div className="flex flex-wrap items-end justify-between gap-6">
          <dl className="grid grid-cols-2 gap-x-10 gap-y-4 sm:grid-cols-4">
            <BandFigure label="Waiting for you" value={data.queue.length} />
            <BandFigure label="Seen today" value={d.seen_today} />
            <BandFigure label="Your median consult" value={d.consult_min} unit="min" />
            <BandFigure label="Follow-ups overdue" value={overdue} />
          </dl>
          <div>
            <p className="mb-2 text-sm font-bold">Your status · patients see it</p>
            <div className="flex flex-wrap gap-1.5 rounded-2xl bg-white/60 p-1.5" role="group" aria-label="Doctor status">
              {(["in", "break", "delayed", "away"] as DoctorStatus[]).map((s) => (
                <button
                  key={s}
                  onClick={() => s !== d.status && act("staff_action", { p: { action: "set_doctor_status", doctor_id: d.id, status: s } }, `You are now: ${DOCTOR_STATUS[s].label}`)}
                  aria-pressed={d.status === s}
                  className={`relative h-10 rounded-xl px-4 text-sm font-bold transition-colors ${d.status === s ? "text-white" : "text-[#0a0a0a] hover:bg-white"}`}
                >
                  {d.status === s && <motion.span layoutId="doc-status" className="absolute inset-0 rounded-xl bg-[#0a0a0a]" transition={{ type: "spring", stiffness: 420, damping: 34 }} />}
                  <span className="relative">{DOCTOR_STATUS[s].label}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      </PageBand>

      <div className="grid gap-6 p-4 sm:p-8 xl:grid-cols-[minmax(0,1fr)_380px]">
        <div>
          {data.serving ? (
            <WithYou key={data.serving.visit_id} s={data.serving} now={now} onAct={act} doctorId={d.id} />
          ) : (
            <section className="grid min-h-[360px] place-items-center rounded-[28px] bg-[#cbe4f2] p-8 text-center">
              <div>
                <p className="text-3xl font-black uppercase tracking-[-0.04em]">No patient with you</p>
                <p className="mt-2 text-lg">{next ? `${next.token} ${next.first_name} is next.` : "Nobody is waiting."}</p>
                <motion.button
                  whileTap={{ scale: 0.97 }}
                  disabled={!next || d.status !== "in"}
                  onClick={() => act("staff_action", { p: { action: "call_next", doctor_id: d.id } })}
                  className="mt-6 h-14 rounded-xl bg-[#b0298f] px-8 text-lg font-black uppercase text-white hover:bg-[#951f78] disabled:opacity-40"
                >
                  Call next{next ? ` → ${next.token}` : ""}
                </motion.button>
                {d.status !== "in" && <p className="mt-3 text-sm">Set your status to In to call patients.</p>}
              </div>
            </section>
          )}
        </div>

        <aside className="space-y-6">
          <section className="overflow-hidden rounded-[24px] bg-white ring-1 ring-[var(--edge)]">
            <h2 className="bg-[#0a0a0a] px-5 py-3 text-lg font-black uppercase tracking-[-0.02em] text-white">Your queue · {data.queue.length}</h2>
            <ol className="max-h-[520px] divide-y divide-[var(--rule)] overflow-y-auto">
              <AnimatePresence initial={false}>
                {data.queue.map((q) => (
                  <motion.li key={q.visit_id} layout="position" initial={{ opacity: 0, x: -16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0 }} className="px-5 py-3">
                    <div className="flex items-center gap-2">
                      <span className="w-5 text-right text-[13px] tabular-nums text-[var(--muted)]">{q.position}</span>
                      <span className="font-black tabular-nums">{q.token}</span>
                      <span className="font-bold">{q.first_name}</span>
                      <BandChip band={q.band} provisional={q.provisional} />
                    </div>
                    <div className="ml-7 mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-[13px] text-[var(--muted)]">
                      <span>waited {now ? minutesSince(q.arrived_at, now) : "–"} min</span>
                      <span>
                        {q.age_group}
                        {q.age_years != null && ` ${q.age_years}y`}
                      </span>
                      {q.history.length > 0 && (
                        <span className="flex items-center gap-1 font-bold text-[#0a0a0a]" title={`Past statuses: ${q.history.map((h) => STATUS_WORD[h]).join(", ")}`}>
                          Returning
                          {q.history.map((h, i) => (
                            <span key={i} className="h-2.5 w-2.5 rounded-full" style={{ background: STATUS_FILL[h] }} />
                          ))}
                        </span>
                      )}
                    </div>
                    {q.doctor_note && <p className="ml-7 mt-1 line-clamp-1 text-[13px]">{q.doctor_note}</p>}
                  </motion.li>
                ))}
              </AnimatePresence>
              {data.queue.length === 0 && <li className="px-5 py-6 text-[var(--muted)]">Nobody waiting.</li>}
            </ol>
          </section>

          <section className="rounded-[24px] bg-white p-5 ring-1 ring-[var(--edge)]">
            <h2 className="text-lg font-black uppercase tracking-[-0.02em]">Follow-ups to chase</h2>
            <p className="mt-1 text-sm text-[var(--muted)]">Your patients whose follow-up date has passed, or falls in the next three days.</p>
            {data.followups.length === 0 ? (
              <p className="mt-3 text-[var(--muted)]">None.</p>
            ) : (
              <ul className="mt-3 space-y-2">
                {data.followups.map((f) => (
                  <li key={f.journey_id} className={`flex items-center justify-between rounded-xl px-3 py-2 text-sm ${f.state === "defaulted" ? "bg-[#ffedea]" : "bg-[#fff1c2]"}`}>
                    <span className="font-bold">{f.first_name}</span>
                    <span>
                      {f.state === "defaulted" ? "Missed" : "Due"} {day(f.follow_up_on)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </aside>
      </div>

      <AnimatePresence>
        {toast && (
          <motion.div
            role="status"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 20 }}
            className="fixed bottom-5 left-1/2 z-40 -translate-x-1/2 rounded-xl bg-[#0a0a0a] px-5 py-3 font-bold text-white shadow-lg"
          >
            {toast}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function WithYou({
  s,
  now,
  doctorId,
  onAct,
}: {
  s: NonNullable<Desk["serving"]>;
  now: number;
  doctorId: string;
  onAct: (fn: string, args: Record<string, unknown>, msg?: string) => Promise<void>;
}) {
  const past = s.journey.filter((j) => j.visit_id !== s.visit_id);
  const t = trend(past.map((j) => j.status));
  const [status, setStatus] = useState<Band>(s.recorded?.status ?? (s.band === "red" ? "red" : s.band));
  const [follow, setFollow] = useState<number | null>(
    // days between this visit and the follow-up already saved for it
    s.recorded?.follow_up_on
      ? Math.round((new Date(`${s.recorded.follow_up_on}T00:00:00+05:30`).getTime() - new Date(s.called_at).setHours(0, 0, 0, 0)) / 86_400_000) || 7
      : null,
  );
  const [consult, setConsult] = useState(s.recorded?.consult_ok ?? false);
  const [notes, setNotes] = useState(s.recorded?.notes ?? "");
  const [busy, setBusy] = useState(false);
  const needs = s.special_needs.map((n) => SPECIAL_NEEDS.find((x) => x.id === n)?.label ?? n);

  const save = async (andNext: boolean) => {
    setBusy(true);
    await onAct(
      "doctor_record",
      { p: { visit_id: s.visit_id, status, follow_up_days: follow, consult_ok: status !== "red" && consult, notes: notes || null } },
      andNext ? undefined : "Saved to the Health Journey",
    );
    if (andNext) await onAct("staff_action", { p: { action: "call_next", doctor_id: doctorId } });
    setBusy(false);
  };

  return (
    <motion.section
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ type: "spring", stiffness: 220, damping: 26 }}
      className="overflow-hidden rounded-[28px] bg-white ring-1 ring-[var(--edge)]"
    >
      <header className="flex flex-wrap items-end justify-between gap-4 bg-[#0a0a0a] p-6 text-white">
        <div>
          <p className="text-sm text-[#b4b4b4]">With you since {hhmm(s.called_at)} · {now ? minutesSince(s.called_at, now) : "–"} min</p>
          <p className="mt-1 text-[clamp(2.5rem,2rem+2vw,3.5rem)] font-black leading-none tracking-[-0.04em]">
            {s.token} <span className="text-[0.6em] tracking-[-0.02em]">{s.first_name}</span>
          </p>
          <p className="mt-2 text-[#d6d6d6]">
            {s.age_group}
            {s.age_years != null && `, ${s.age_years} years`} · speaks {LANGUAGE_NAMES[s.language] ?? s.language}
            {needs.length > 0 && ` · ${needs.join(", ")}`}
          </p>
        </div>
        <BandChip band={s.band} provisional={s.provisional} size="lg" />
      </header>

      <div className="grid gap-6 p-6 lg:grid-cols-2">
        <div className="space-y-6">
          <div className="rounded-2xl bg-[#cbe4f2] p-5">
            <h3 className="text-sm font-black uppercase tracking-[-0.01em]">Symptom note</h3>
            <p className="mt-2 text-lg leading-snug">{s.doctor_note ?? "No note from the symptom check."}</p>
            <p className="mt-2 text-xs text-[#2d4a58]">From the app’s guided symptom check. Not a diagnosis.</p>
          </div>

          <div>
            <div className="flex items-baseline justify-between">
              <h3 className="text-sm font-black uppercase tracking-[-0.01em]">Health Journey</h3>
              {t && (
                <span className={`text-sm font-bold ${t === "Improving" ? "text-[#1f6b3f]" : t === "Getting worse" ? "text-[#a3161c]" : "text-[var(--muted)]"}`}>
                  {t}
                </span>
              )}
            </div>
            {past.length === 0 ? (
              <p className="mt-2 text-[var(--muted)]">First visit. No earlier statuses.</p>
            ) : (
              <ol className="mt-3 flex items-stretch gap-2 overflow-x-auto pb-1">
                {past.map((j, i) => (
                  <motion.li
                    key={j.visit_id}
                    initial={{ opacity: 0, y: 12 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: i * 0.08 }}
                    className="flex min-w-[150px] flex-1 flex-col rounded-2xl bg-[#f1f4f6] p-3"
                  >
                    <span className="text-xs font-bold text-[var(--muted)]">{day(j.day)} · {j.hospital}</span>
                    <span className="mt-2">
                      <BandChip band={j.status} />
                    </span>
                    {j.notes && <span className="mt-2 text-[13px] leading-snug">{j.notes}</span>}
                    <span className="mt-auto pt-2 text-xs text-[var(--muted)]">{j.doctor}</span>
                  </motion.li>
                ))}
              </ol>
            )}
          </div>
        </div>

        <form
          className="space-y-5 rounded-2xl bg-[#f1e3b5] p-5"
          onSubmit={(e) => {
            e.preventDefault();
            void save(true);
          }}
        >
          <h3 className="text-sm font-black uppercase tracking-[-0.01em]">Record this visit</h3>
          <div>
            <p className="mb-2 text-sm font-bold">Status after this consultation</p>
            <div className="grid grid-cols-3 gap-2">
              {(["green", "yellow", "red"] as Band[]).map((b) => (
                <button
                  key={b}
                  type="button"
                  aria-pressed={status === b}
                  onClick={() => setStatus(b)}
                  className={`flex min-h-16 flex-col items-start justify-center rounded-xl px-3 text-left text-sm font-bold transition ${
                    status === b ? "ring-4 ring-[#0a0a0a]" : "opacity-80 hover:opacity-100"
                  }`}
                  style={{ background: b === "red" ? "#c4242a" : b === "yellow" ? "#ffc013" : "#c9eed6", color: b === "red" ? "#fff" : "#0a0a0a" }}
                >
                  {b === "red" ? "◆" : b === "yellow" ? "▲" : "●"} {STATUS_WORD[b]}
                </button>
              ))}
            </div>
          </div>
          <div>
            <p className="mb-2 text-sm font-bold">Follow-up</p>
            <div className="flex flex-wrap gap-2">
              {FOLLOW_UPS.map((f) => (
                <button
                  key={f.label}
                  type="button"
                  aria-pressed={follow === f.days}
                  onClick={() => setFollow(f.days)}
                  className={`h-10 rounded-xl px-3 text-sm font-bold ${follow === f.days ? "bg-[#0a0a0a] text-white" : "bg-white hover:bg-white/70"}`}
                >
                  {f.label}
                </button>
              ))}
            </div>
            {follow && <p className="mt-2 text-xs">The patient gets a reminder by app and SMS two days before, in their language.</p>}
          </div>
          <label className={`flex items-center gap-3 text-sm ${status === "red" ? "opacity-50" : ""}`}>
            <input type="checkbox" checked={consult && status !== "red"} disabled={status === "red"} onChange={(e) => setConsult(e.target.checked)} className="h-5 w-5" />
            <span>
              <strong>Text consult suitable</strong> for the follow-up {status === "red" && "(never for Emergency now)"}
            </span>
          </label>
          <label className="block text-sm font-bold">
            Note for the Health Journey
            <input
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              maxLength={500}
              placeholder="e.g. BP 150/95, tablets changed"
              className="mt-1.5 h-11 w-full rounded-xl bg-white px-3 font-normal"
            />
          </label>
          <div className="flex flex-wrap gap-2 pt-1">
            <motion.button
              whileTap={{ scale: 0.97 }}
              disabled={busy}
              className="h-12 flex-1 rounded-xl bg-[#b0298f] px-5 font-black uppercase text-white hover:bg-[#951f78] disabled:opacity-60"
            >
              Save &amp; call next
            </motion.button>
            <button type="button" disabled={busy} onClick={() => void save(false)} className="h-12 rounded-xl bg-[#0a0a0a] px-5 font-bold text-white disabled:opacity-60">
              Save
            </button>
          </div>
          <button
            type="button"
            onClick={() =>
              onAct("staff_action", { p: { action: "send_to_emergency", visit_id: s.visit_id, reason_code: "clinical_review" } }, `${s.token} sent to Emergency`)
            }
            className="w-full rounded-xl border-2 border-[#c4242a] py-2.5 text-sm font-bold text-[#a3161c] hover:bg-[#ffedea]"
          >
            Send to Emergency now
          </button>
        </form>
      </div>
    </motion.section>
  );
}
