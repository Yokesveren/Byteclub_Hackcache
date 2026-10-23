"use client";

import { AnimatePresence, motion } from "motion/react";
import { useState } from "react";
import { BandChip } from "@/components/BandChip";
import { ReasonDialog } from "@/components/ReasonDialog";
import { useShell, type ShellContext } from "@/components/StaffShell";
import { BandFigure, PageBand } from "@/components/PageBand";
import { UrgentBanner } from "@/components/UrgentBanner";
import { DOCTOR_STATUS, floorName, hhmm, LANGUAGE_NAMES, minutesSince, SPECIAL_NEEDS } from "@/lib/format";
import { useLiveData } from "@/lib/hooks";
import { rpc } from "@/lib/supabase";
import type { Band, Department, Doctor, DoctorStatus, Facility, QueueItem } from "@/lib/types";

export default function FacilityPage() {
  return <FacilityView {...useShell()} />;
}

type Pending =
  | { kind: "move_up" | "move_down" | "move_end" | "no_show" | "send_to_emergency"; item: QueueItem | { visit_id: string; token: string; first_name: string } }
  | { kind: "set_band"; item: QueueItem; band: Band }
  | { kind: "move_department"; item: QueueItem; departmentId: string }
  | { kind: "assign_doctor"; item: QueueItem; departmentId: string }
  | { kind: "doctor_status"; doctor: Doctor };

const ACTION_TITLES: Record<string, string> = {
  move_up: "Move up one place",
  move_down: "Move down one place",
  move_end: "Move to end of queue",
  no_show: "Mark as no-show",
  send_to_emergency: "Send to Emergency",
  set_band: "Change band",
  move_department: "Move to another department",
  assign_doctor: "Move to another doctor",
};

function FacilityView({ staff, hospitalId, now }: ShellContext) {
  const { data, error, connected, updatedAt, refresh } = useLiveData(
    () => rpc<Facility>("get_facility", { p_hospital_id: hospitalId }),
    { hospitalId, tables: ["urgent_flags", "visits", "doctors"] },
  );
  const [dept, setDept] = useState<string>("all");
  const [pending, setPending] = useState<Pending | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [allFair, setAllFair] = useState(false);

  const act = async (payload: Record<string, unknown>) => {
    const res = await rpc<{ ok: boolean; called?: string | null }>("staff_action", { p: payload });
    if (payload.action === "call_next") setToast(res.called ? `Called ${res.called}` : "No one waiting for this doctor");
    setTimeout(() => setToast(null), 3000);
    await refresh();
  };

  const quick = async (payload: Record<string, unknown>) => {
    try {
      await act(payload);
    } catch (e) {
      setToast(e instanceof Error ? e.message : String(e));
      setTimeout(() => setToast(null), 5000);
    }
  };

  if (!data) {
    return <div className="p-8 text-[var(--muted)]">{error ? `Could not load: ${error}` : "Loading facility…"}</div>;
  }

  const myDoctor = staff.role === "doctor" ? staff.doctor_id : null;
  const doctors = data.doctors.filter(
    (d) => (myDoctor ? d.id === myDoctor : true) && (dept === "all" || d.department_id === dept) && d.status !== "off",
  );
  const deptName = (id: string) => data.departments.find((d) => d.id === id)?.name ?? id;
  const stale = !!error;
  const worst = [...data.departments].sort((a, b) => b.waiting - a.waiting)[0];
  const worstDoctors = worst ? data.doctors.filter((d) => d.department_id === worst.id && d.status !== "off").length : 0;
  const finding =
    data.urgent_flags.length > 0
      ? `${data.urgent_flags.length} urgent ${data.urgent_flags.length === 1 ? "request is" : "requests are"} waiting for a staff member to tap Seen.`
      : worst && worst.level !== "green"
        ? `${worst.name} is ${worst.level === "red" ? "overcrowded" : "busy"}: ${worst.waiting} waiting for ${worstDoctors} ${worstDoctors === 1 ? "doctor" : "doctors"}.`
        : "Every department is within its normal load.";

  return (
    <div>
      <UrgentBanner flags={data.urgent_flags} now={now} onSeen={refresh} />

      <PageBand
        tone="sky"
        title="Facility"
        lead={
          <>
            <span className="font-bold">{data.hospital.name}.</span> {finding}
          </>
        }
        aside={
          <span className={stale ? "font-bold text-[#a3161c]" : ""} title="Live updates arrive within about a second; the page also refreshes every 15 seconds">
            <span className="relative mr-2 inline-block h-2.5 w-2.5 rounded-full align-middle" style={{ background: connected ? "#1f9d55" : "#8a96a0" }} aria-hidden>
              {connected && (
                <motion.span
                  className="absolute inset-0 rounded-full bg-[#1f9d55]"
                  animate={{ scale: [1, 2.6], opacity: [0.6, 0] }}
                  transition={{ duration: 1.6, repeat: Infinity }}
                />
              )}
            </span>
            {connected ? "Live" : "Reconnecting"} · updated {updatedAt ? hhmm(data.now) : "never"}
            {error && ` · ${error}`}
          </span>
        }
      >
        <dl className="grid grid-cols-2 gap-x-8 gap-y-5 sm:grid-cols-3 xl:grid-cols-5">
          <BandFigure label="Waiting now" value={<Roll value={data.stats.waiting} />} />
          <BandFigure label="Seen today" value={<Roll value={data.stats.seen} />} />
          <BandFigure label="Band not yet confirmed" value={<Roll value={data.stats.unconfirmed} />} note={data.stats.unconfirmed > 0 ? "needs triage" : undefined} />
          <BandFigure label="Urgent requests today" value={<Roll value={data.stats.flags_today} />} />
          <BandFigure
            label="Median time to Seen"
            value={<Roll value={data.stats.median_ack_sec == null ? "–" : data.stats.median_ack_sec} />}
            unit={data.stats.median_ack_sec == null ? undefined : "s"}
          />
        </dl>

        <div className="mt-7 flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Departments">
          <DeptTab active={dept === "all"} onClick={() => setDept("all")} label="All departments" count={data.stats.waiting} />
          {data.departments.map((d) => (
            <DeptTab key={d.id} active={dept === d.id} onClick={() => setDept(d.id)} label={d.name} count={d.waiting} level={d.level} />
          ))}
        </div>
      </PageBand>

      {data.fairness.length > 0 && (
        <div className="mx-4 mt-5 rounded-2xl bg-[#ffc013] px-5 py-3.5 text-sm text-[#0a0a0a] sm:mx-8">
          <p>
            <strong>
              Fairness check: {data.fairness.length} Routine {data.fairness.length === 1 ? "patient has" : "patients have"} waited past the
              limit or been overtaken too often.
            </strong>{" "}
            Nothing is reordered automatically; a supervisor decides.
          </p>
          <ul className="mt-1.5 flex flex-wrap gap-x-5 gap-y-1 tabular-nums">
            {[...data.fairness]
              .sort((a, b) => b.waited_min - a.waited_min)
              .slice(0, allFair ? undefined : 3)
              .map((f) => (
                <li key={f.visit_id}>
                  <strong>{f.token}</strong> waited {f.waited_min} min{f.overtaken > 0 && `, overtaken ${f.overtaken}×`}
                </li>
              ))}
            {data.fairness.length > 3 && (
              <li>
                <button onClick={() => setAllFair((v) => !v)} className="font-bold underline underline-offset-2">
                  {allFair ? "Show fewer" : `Show all ${data.fairness.length}`}
                </button>
              </li>
            )}
          </ul>
        </div>
      )}

      {data.unassigned.length > 0 && (
        <div className="mx-4 mt-4 rounded-2xl bg-[#c4242a] px-5 py-3.5 text-sm text-white sm:mx-8">
          <strong>No doctor on duty for:</strong> {data.unassigned.map((u) => `${u.token} (${deptName(u.department_id)})`).join(", ")}
        </div>
      )}

      <div className="flex gap-4 overflow-x-auto px-4 py-6 sm:px-8">
        {doctors.map((d) => (
          <DoctorColumn
            key={d.id}
            doctor={d}
            departmentName={deptName(d.department_id)}
            now={now}
            onQuick={quick}
            onPending={setPending}
          />
        ))}
        {doctors.length === 0 && <p className="text-[var(--muted)]">No doctors on duty in this view.</p>}
      </div>

      {toast && (
        <div className="fixed bottom-4 left-1/2 z-40 -translate-x-1/2 rounded-lg bg-[var(--ink)] px-4 py-2 text-white shadow-lg" role="status"> {/* taste-skill-ignore: toast floats */}
          {toast}
        </div>
      )}

      {pending && pending.kind === "doctor_status" && (
        <DoctorStatusDialog
          doctor={pending.doctor}
          onCancel={() => setPending(null)}
          onConfirm={async (status, note) => {
            await act({ action: "set_doctor_status", doctor_id: pending.doctor.id, status, note });
            setPending(null);
          }}
        />
      )}

      {pending && pending.kind !== "doctor_status" && (
        <PendingDialog
          pending={pending}
          departments={data.departments}
          doctors={data.doctors}
          onCancel={() => setPending(null)}
          onConfirm={async (payload) => {
            await act(payload);
            setPending(null);
          }}
        />
      )}
    </div>
  );
}

/** A number that rolls to its new value. */
function Roll({ value }: { value: number | string }) {
  return (
    <span className="relative inline-block overflow-hidden align-bottom">
      <AnimatePresence mode="popLayout" initial={false}>
        <motion.span
          key={String(value)}
          className="inline-block"
          initial={{ y: "100%", opacity: 0 }}
          animate={{ y: "0%", opacity: 1 }}
          exit={{ y: "-100%", opacity: 0 }}
          transition={{ type: "spring", stiffness: 380, damping: 30 }}
        >
          {value}
        </motion.span>
      </AnimatePresence>
    </span>
  );
}

/** Department pill on the band: an ink pill slides to the selected one; load shows as a mark plus its word. */
function DeptTab({
  active,
  onClick,
  label,
  count,
  level,
}: {
  active: boolean;
  onClick: () => void;
  label: string;
  count: number;
  level?: "green" | "amber" | "red";
}) {
  return (
    <button
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={`relative flex h-11 shrink-0 items-center gap-2 rounded-full px-4 text-sm font-bold transition-colors ${
        active ? "text-white" : "bg-white/55 text-[#0a0a0a] hover:bg-white/85"
      }`}
    >
      {active && (
        <motion.span
          layoutId="dept-pill"
          className="absolute inset-0 rounded-full bg-[#0a0a0a]"
          transition={{ type: "spring", stiffness: 420, damping: 34 }}
        />
      )}
      <span className="relative z-10 flex items-center gap-2">
        {label}
        <span className="tabular-nums opacity-80">{count}</span>
        {level && level !== "green" && (
          <span
            className={`rounded-full px-2 py-0.5 text-xs ${
              level === "red" ? "bg-[#c4242a] text-white" : "bg-[#ffc013] text-[#0a0a0a]"
            }`}
          >
            {level === "red" ? "Overcrowded" : "Busy"}
          </span>
        )}
      </span>
    </button>
  );
}

function DoctorColumn({
  doctor: d,
  departmentName,
  now,
  onQuick,
  onPending,
}: {
  doctor: Doctor;
  departmentName: string;
  now: number;
  onQuick: (p: Record<string, unknown>) => void;
  onPending: (p: Pending) => void;
}) {
  const status = DOCTOR_STATUS[d.status];
  const [open, setOpen] = useState<string | null>(null);
  const yellow = d.queue.filter((q) => q.band === "yellow").length;

  return (
    <motion.section
      layout
      initial={{ opacity: 0, y: 24 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ type: "spring", stiffness: 220, damping: 26 }}
      className="flex w-[340px] shrink-0 flex-col overflow-hidden rounded-[20px] bg-white ring-1 ring-[var(--edge)]"
    >
      <header className="bg-[#0a0a0a] p-4 text-white">
        <div className="flex items-start justify-between gap-2">
          <div>
            <h2 className="text-lg font-black uppercase leading-tight tracking-[-0.02em]">{d.room}</h2>
            <p className="mt-0.5 text-sm font-bold">{d.name}</p>
            <p className="text-[13px] text-[#b4b4b4]">
              {departmentName} · {d.room}, {floorName(d.floor)}
            </p>
          </div>
          <button
            onClick={() => onPending({ kind: "doctor_status", doctor: d })}
            className={`shrink-0 whitespace-nowrap rounded-full border px-2 py-0.5 text-xs font-bold ${status.className}`}
            title="Change doctor status"
          >
            {status.label} ▾
          </button>
        </div>
        {d.status_note && <p className="mt-1 text-[13px] text-[#ffc013]">“{d.status_note}”</p>}
        <p className="mt-2 text-[13px] tabular-nums text-[#b4b4b4]">
          {d.queue.length} waiting ({yellow} Priority) · seen {d.seen_today} · median {d.consult_min} min
        </p>
      </header>

      <div className="border-b border-[var(--rule)] bg-[#cbe4f2] p-4">
        {d.serving ? (
          <div>
            <p className="text-[13px] text-[#2d4a58]">With the doctor since {hhmm(d.serving.called_at)}</p>
            <motion.p
              key={`${d.serving.token}-${d.serving.call_count}`}
              className="text-[28px] font-black leading-none tracking-[-0.03em]"
              initial={{ rotateX: -80, opacity: 0 }}
              animate={{ rotateX: 0, opacity: 1 }}
              transition={{ type: "spring", stiffness: 260, damping: 22 }}
              style={{ transformPerspective: 500 }}
            >
              {d.serving.token} <span className="text-lg font-bold tracking-normal">{d.serving.first_name}</span>
              {d.serving.call_count > 1 && <span className="ml-2 text-[13px] font-normal text-[var(--muted)]">called {d.serving.call_count} times</span>}
            </motion.p>
            <div className="mt-2 flex flex-wrap gap-1.5">
              <SmallBtn onClick={() => onQuick({ action: "recall", visit_id: d.serving!.visit_id })}>Call again</SmallBtn>
              <SmallBtn
                onClick={() =>
                  onPending({
                    kind: "no_show",
                    item: { visit_id: d.serving!.visit_id, token: d.serving!.token, first_name: d.serving!.first_name },
                  })
                }
              >
                No-show
              </SmallBtn>
              <SmallBtn onClick={() => onQuick({ action: "complete", visit_id: d.serving!.visit_id })}>Done</SmallBtn>
            </div>
          </div>
        ) : (
          <p className="text-sm font-bold text-[#2d4a58]">No patient with the doctor.</p>
        )}
        <button
          onClick={() => onQuick({ action: "call_next", doctor_id: d.id })}
          disabled={d.queue.length === 0 && !d.serving}
          className="mt-3 min-h-12 w-full rounded-xl bg-[#b0298f] text-base font-black uppercase tracking-[-0.01em] text-white transition-transform hover:bg-[#951f78] active:scale-[0.98] disabled:opacity-40"
        >
          Call next{d.queue[0] ? ` → ${d.queue[0].token}` : ""}
        </button>
      </div>

      <ol className="max-h-[60vh] flex-1 divide-y divide-[var(--rule)] overflow-y-auto overflow-x-hidden">
        <AnimatePresence initial={false}>
        {d.queue.map((q) => (
          <motion.li
            key={q.visit_id}
            layout="position"
            initial={{ opacity: 0, x: -24, backgroundColor: "#fff1c2" }}
            animate={{ opacity: 1, x: 0, backgroundColor: q.has_flag ? "#ffedea" : "rgba(255,255,255,0)" }}
            exit={{ opacity: 0, x: 40, transition: { duration: 0.2 } }}
            transition={{ type: "spring", stiffness: 380, damping: 32, backgroundColor: { duration: 1.2 } }}
          >
            <button
              onClick={() => setOpen(open === q.visit_id ? null : q.visit_id)}
              className="flex w-full items-start gap-2 px-4 py-2.5 text-left hover:bg-[var(--raised)]"
              aria-expanded={open === q.visit_id}
            >
              <span className="w-5 pt-0.5 text-right text-[13px] tabular-nums text-[var(--muted)]">{q.position}</span>
              <span className="flex-1">
                <span className="flex flex-wrap items-center gap-1.5">
                  <span className="font-bold tabular-nums">{q.token}</span>
                  <span>{q.first_name}</span>
                  <BandChip band={q.band} provisional={q.provisional} />
                </span>
                <span className="mt-0.5 block text-[13px] text-[var(--muted)]">
                  waited {now ? minutesSince(q.arrived_at, now) : q.waited_min} min · {q.age_group}
                  {q.age_years != null && ` ${q.age_years}y`} · {LANGUAGE_NAMES[q.language] ?? q.language}
                  {q.channel !== "app" && ` · ${q.channel}`}
                </span>
                <span className="mt-0.5 flex flex-wrap gap-1 text-xs">
                  {q.has_flag && <Tag className="bg-[#c4242a] text-white">Urgent request</Tag>}
                  {q.needs_stair_free && <Tag>Stair-free route</Tag>}
                  {q.needs_visual_call && <Tag>Visual call</Tag>}
                  {q.no_show_count > 0 && <Tag>Missed call {q.no_show_count}</Tag>}
                  {q.special_needs
                    .filter((s) => !["walking", "wheelchair", "hearing"].includes(s))
                    .map((s) => {
                      const n = SPECIAL_NEEDS.find((x) => x.id === s);
                      return <Tag key={s}>{n ? n.label : s}</Tag>;
                    })}
                </span>
              </span>
            </button>
            <AnimatePresence initial={false}>
            {open === q.visit_id && (
              <motion.div
                className="space-y-2 overflow-hidden bg-[var(--raised)] px-4 pb-3 pl-11"
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: "auto", opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                transition={{ type: "spring", stiffness: 400, damping: 36 }}
              >
                {q.doctor_note && (
                  <p className="rounded-md bg-white p-2.5 text-[13px]">
                    <span className="font-bold">Symptom note:</span> {q.doctor_note}
                  </p>
                )}
                <div className="flex flex-wrap gap-1.5">
                  {q.provisional && (
                    <SmallBtn
                      primary
                      onClick={() => onQuick({ action: "confirm_band", visit_id: q.visit_id, band: q.band, reason_code: "clinical_review" })}
                    >
                      Confirm {q.band === "yellow" ? "Priority" : "Routine"}
                    </SmallBtn>
                  )}
                  <SmallBtn onClick={() => onPending({ kind: "set_band", item: q, band: q.band === "yellow" ? "green" : "yellow" })}>
                    Change to {q.band === "yellow" ? "Routine" : "Priority"}
                  </SmallBtn>
                  <SmallBtn onClick={() => onPending({ kind: "move_up", item: q })} disabled={q.position === 1}>
                    Move up
                  </SmallBtn>
                  <SmallBtn onClick={() => onPending({ kind: "move_down", item: q })}>Move down</SmallBtn>
                  <SmallBtn onClick={() => onPending({ kind: "move_end", item: q })}>End of queue</SmallBtn>
                  <SmallBtn onClick={() => onPending({ kind: "assign_doctor", item: q, departmentId: d.department_id })}>
                    Other doctor
                  </SmallBtn>
                  <SmallBtn onClick={() => onPending({ kind: "move_department", item: q, departmentId: d.department_id })}>Other department</SmallBtn>
                  <SmallBtn danger onClick={() => onPending({ kind: "send_to_emergency", item: q })}>
                    Send to Emergency
                  </SmallBtn>
                </div>
              </motion.div>
            )}
            </AnimatePresence>
          </motion.li>
        ))}
        </AnimatePresence>
        {d.queue.length === 0 && <li className="px-3 py-4 text-sm text-[var(--muted)]">Queue is empty.</li>}
      </ol>
    </motion.section>
  );
}

function Tag({ children, className = "bg-[#eef2f5] text-[#2b363d]" }: { children: React.ReactNode; className?: string }) {
  return <span className={`rounded px-1.5 py-0.5 ${className}`}>{children}</span>;
}

function SmallBtn({
  children,
  onClick,
  disabled,
  primary,
  danger,
}: {
  children: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
  primary?: boolean;
  danger?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`min-h-8 rounded border px-2 text-xs font-semibold disabled:opacity-40 ${
        danger
          ? "border-[#c4242a] bg-white text-[#a3161c] hover:bg-[#ffedea]"
          : primary
            ? "border-[var(--ink)] bg-[var(--ink)] text-white hover:bg-[#2a2a2a]"
            : "border-[var(--control)] bg-white hover:bg-[var(--raised)]"
      }`}
    >
      {children}
    </button>
  );
}

function PendingDialog({
  pending,
  departments,
  doctors,
  onCancel,
  onConfirm,
}: {
  pending: Exclude<Pending, { kind: "doctor_status" }>;
  departments: Department[];
  doctors: Doctor[];
  onCancel: () => void;
  onConfirm: (payload: Record<string, unknown>) => Promise<void>;
}) {
  const item = pending.item;
  const [target, setTarget] = useState<string>(() => {
    if (pending.kind === "move_department") return departments.find((d) => d.id !== pending.departmentId)?.id ?? "";
    if (pending.kind === "assign_doctor") return doctors.find((d) => d.department_id === pending.departmentId && d.status !== "off")?.id ?? "";
    return "";
  });

  const defaults: Record<string, string> = {
    no_show: "no_show",
    move_end: "patient_request",
    set_band: "clinical_review",
    send_to_emergency: "clinical_review",
    assign_doctor: "load_balance",
    move_department: "wrong_department",
  };

  let description = `${item.token} · ${item.first_name}`;
  if (pending.kind === "set_band") {
    description += ` will be shown as ${pending.band === "yellow" ? "Priority" : "Routine"}. The app can never lower a band staff have set.`;
  }
  if (pending.kind === "send_to_emergency") {
    description += " leaves the OPD queue. The patient's phone will tell them to go to Emergency now.";
  }

  return (
    <ReasonDialog
      title={ACTION_TITLES[pending.kind]}
      description={description}
      defaultReason={defaults[pending.kind]}
      danger={pending.kind === "send_to_emergency"}
      confirmLabel={pending.kind === "send_to_emergency" ? "Send to Emergency" : "Confirm"}
      onCancel={onCancel}
      onConfirm={(reason, note) =>
        onConfirm({
          action: pending.kind,
          visit_id: item.visit_id,
          reason_code: reason,
          note,
          ...(pending.kind === "set_band" ? { band: pending.band } : {}),
          ...(pending.kind === "move_department" ? { department_id: target } : {}),
          ...(pending.kind === "assign_doctor" ? { doctor_id: target } : {}),
        })
      }
    >
      {(pending.kind === "move_department" || pending.kind === "assign_doctor") && (
        <label className="mt-4 block text-sm font-semibold">
          {pending.kind === "move_department" ? "New department" : "New doctor"}
          <select value={target} onChange={(e) => setTarget(e.target.value)} className="mt-1 w-full rounded border border-[var(--control)] px-2 py-1.5 font-normal">
            {pending.kind === "move_department"
              ? departments.filter((d) => d.id !== pending.departmentId).map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name} ({d.waiting} waiting)
                  </option>
                ))
              : doctors
                  .filter((d) => d.department_id === pending.departmentId && d.status !== "off")
                  .map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name}, {d.room} · {DOCTOR_STATUS[d.status].label} · {d.queue.length} waiting
                    </option>
                  ))}
          </select>
        </label>
      )}
    </ReasonDialog>
  );
}

function DoctorStatusDialog({
  doctor,
  onCancel,
  onConfirm,
}: {
  doctor: Doctor;
  onCancel: () => void;
  onConfirm: (status: DoctorStatus, note: string) => Promise<void>;
}) {
  const [status, setStatus] = useState<DoctorStatus>(doctor.status);
  const [note, setNote] = useState(doctor.status_note ?? "");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={onCancel}>
      <form
        onClick={(e) => e.stopPropagation()}
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          try {
            await onConfirm(status, note);
          } catch (err) {
            setError(err instanceof Error ? err.message : String(err));
            setBusy(false);
          }
        }}
        className="w-full max-w-sm rounded-xl bg-white p-5 shadow-xl" /* taste-skill-ignore: dialog */
        role="dialog"
        aria-modal="true"
      >
        <h2 className="text-lg font-bold">{doctor.name}: status</h2>
        <p className="mt-1 text-sm text-[var(--muted)]">
          Patients in this queue see the change. Break and away pause their time windows.
        </p>
        <div className="mt-3 grid grid-cols-2 gap-2">
          {(Object.keys(DOCTOR_STATUS) as DoctorStatus[]).map((s) => (
            <label
              key={s}
              className={`flex cursor-pointer items-center gap-2 rounded border px-2 py-2 text-sm ${
                status === s ? "border-[var(--ink)] bg-[var(--raised)] font-semibold" : "border-[var(--edge)]"
              }`}
            >
              <input type="radio" name="status" checked={status === s} onChange={() => setStatus(s)} />
              {DOCTOR_STATUS[s].label}
            </label>
          ))}
        </div>
        <label className="mt-3 block text-sm font-semibold">
          Note shown to patients (optional)
          <input
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="e.g. Back at 11:30"
            maxLength={280}
            className="mt-1 w-full rounded border border-[var(--control)] px-2 py-1.5 font-normal"
          />
        </label>
        {error && <p className="mt-3 text-sm font-semibold text-red-700">{error}</p>}
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onCancel} className="rounded border border-[var(--control)] px-4 py-2">
            Cancel
          </button>
          <button type="submit" disabled={busy} className="rounded bg-[var(--ink)] px-4 py-2 font-semibold text-white disabled:opacity-60">
            {busy ? "Saving…" : "Save"}
          </button>
        </div>
      </form>
    </div>
  );
}
