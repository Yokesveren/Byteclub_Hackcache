"use client";

import { useEffect, useState } from "react";
import { PageBand } from "@/components/PageBand";
import { useShell, type ShellContext } from "@/components/StaffShell";
import { hhmmss } from "@/lib/format";
import { rpc } from "@/lib/supabase";

export default function DemoPage() {
  return <DemoControls {...useShell()} />;
}

const SPEEDS = [1, 5, 15, 30, 60];

function DemoControls({ hospitalId, now, speed }: ShellContext) {
  const [log, setLog] = useState<string[]>([]);
  const [autopilot, setAutopilot] = useState(false);
  const [busy, setBusy] = useState(false);

  const note = (msg: string) => setLog((l) => [`${hhmmss(Date.now())}  ${msg}`, ...l].slice(0, 30));

  const run = async (label: string, fn: string, args?: Record<string, unknown>) => {
    setBusy(true);
    try {
      const res = await rpc<unknown>(fn, args);
      note(`${label}: ${typeof res === "object" ? JSON.stringify(res) : String(res)}`);
    } catch (e) {
      note(`${label} failed: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setBusy(false);
    }
  };

  // Autopilot: doctors call the next patient when their usual consult time has passed.
  useEffect(() => {
    if (!autopilot) return;
    const id = setInterval(async () => {
      try {
        const n = await rpc<number>("demo_step", { p_hospital: null });
        if (n > 0) note(`Autopilot: ${n} doctor(s) called the next patient`);
      } catch (e) {
        note(`Autopilot error: ${e instanceof Error ? e.message : String(e)}`);
      }
    }, 3000);
    return () => clearInterval(id);
  }, [autopilot]);

  return (
    <div>
    <PageBand
      tone="magenta"
      title="Demo controls"
      lead={
        <>
          Simulated data: every hospital, doctor and patient is made up. These controls act on hospital <strong>{hospitalId}</strong>{" "}
          (switch it in the sidebar).
        </>
      }
    />
    <div className="mx-auto max-w-5xl space-y-6 p-4 sm:p-8">

      <Panel title="Time">
        <p className="mb-3 text-sm text-[var(--muted)]">
          Hospital time is <strong className="tabular-nums">{hhmmss(now)}</strong> running at <strong>{speed}×</strong>. Wait
          windows, the board and the dashboard all follow this clock.
        </p>
        <div className="flex flex-wrap gap-2">
          {SPEEDS.map((s) => (
            <Btn key={s} active={speed === s} disabled={busy} onClick={() => run(`Speed ${s}×`, "demo_set_clock", { p_speed: s, p_jump_min: 0 })}>
              {s}×
            </Btn>
          ))}
          <Btn disabled={busy} onClick={() => run("Jump +15 min", "demo_set_clock", { p_speed: null, p_jump_min: 15 })}>
            Jump +15 min
          </Btn>
          <Btn disabled={busy} onClick={() => run("Jump +60 min", "demo_set_clock", { p_speed: null, p_jump_min: 60 })}>
            Jump +1 hour
          </Btn>
        </div>
        <label className="mt-4 flex items-center gap-2 text-sm">
          <input type="checkbox" checked={autopilot} onChange={(e) => setAutopilot(e.target.checked)} />
          <span>
            <strong>Autopilot</strong>: doctors who are “In” call the next patient when their usual consultation time has
            passed. Combine with a faster clock to make queues move on stage.
          </span>
        </label>
      </Panel>

      <Panel title="Patients and events">
        <div className="flex flex-wrap gap-2">
          <Btn disabled={busy} onClick={() => run("Added patients", "demo_add_patients", { p_hospital: hospitalId, p_count: 5 })}>
            + 5 walk-in patients
          </Btn>
          <Btn disabled={busy} onClick={() => run("Added patients", "demo_add_patients", { p_hospital: hospitalId, p_count: 20 })}>
            + 20 (crowd surge)
          </Btn>
          <Btn danger disabled={busy} onClick={() => run("Urgent flag", "demo_trigger_flag", { p_hospital: hospitalId })}>
            Trigger urgent-help flag
          </Btn>
        </div>
      </Panel>

      <Panel title="Reset">
        <p className="mb-3 text-sm text-[var(--muted)]">
          Wipes all visits, flags and logs, then rebuilds a 10:30 AM OPD across all three hospitals.
        </p>
        <Btn
          danger
          disabled={busy}
          onClick={() => {
            if (window.confirm("Reset all demo data?")) void run("Reset", "demo_reset");
          }}
        >
          Reset demo data
        </Btn>
      </Panel>

      <Panel title="Activity">
        <ol className="max-h-64 overflow-y-auto text-xs text-[#2b363d]">
          {log.map((l, i) => (
            <li key={i} className="border-b border-[var(--rule)] py-1">
              {l}
            </li>
          ))}
          {log.length === 0 && <li className="text-[var(--muted)]">Nothing yet.</li>}
        </ol>
      </Panel>
    </div>
    </div>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-[24px] bg-white p-6 ring-1 ring-[var(--edge)]">
      <h2 className="mb-4 text-2xl font-black uppercase tracking-[-0.03em]">{title}</h2>
      {children}
    </section>
  );
}

function Btn({
  children,
  onClick,
  disabled,
  active,
  danger,
}: {
  children: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
  active?: boolean;
  danger?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`min-h-12 rounded-xl px-5 font-bold transition active:scale-[0.97] disabled:opacity-50 ${
        danger
          ? "bg-[#c4242a] text-white hover:bg-[#a3161c]"
          : active
            ? "bg-[#ffc013] text-[#0a0a0a]"
            : "bg-[#f1f4f6] text-[#0a0a0a] hover:bg-[#cbe4f2]"
      }`}
    >
      {children}
    </button>
  );
}
