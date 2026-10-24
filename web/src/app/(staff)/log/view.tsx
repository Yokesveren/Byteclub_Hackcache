"use client";

import { useState } from "react";
import { PageBand } from "@/components/PageBand";
import { useShell, type ShellContext } from "@/components/StaffShell";
import { EVENT_LABELS, hhmmss, REASONS } from "@/lib/format";
import { useLiveData } from "@/lib/hooks";
import { rpc } from "@/lib/supabase";
import type { LogEntry } from "@/lib/types";

export default function LogPage() {
  return <TransparencyLog {...useShell()} />;
}

const reasonLabel = (code: string | null) => REASONS.find((r) => r.code === code)?.label ?? code ?? "";

function describe(e: LogEntry): string {
  const f = e.from_value ?? {};
  const t = e.to_value ?? {};
  switch (e.type) {
    case "set_band":
    case "confirm_band":
      return `${String(f.band ?? "")} → ${String(t.band ?? "")}`;
    case "doctor_status":
      return `${String(f.status ?? "")} → ${String(t.status ?? "")}`;
    case "move_department":
    case "token_confirmed":
      return `${String(f.token ?? "")} → ${String(t.token ?? "")}`;
    case "assign_doctor":
      return `${String(f.doctor_id ?? "")} → ${String(t.doctor_id ?? "")}`;
    default:
      return "";
  }
}

/** Every queue decision: who, what, from/to, why, when (PRD 10.5, S10). */
function TransparencyLog({ hospitalId }: ShellContext) {
  const [type, setType] = useState("");
  const [actor, setActor] = useState("");
  const { data, error } = useLiveData(
    () => rpc<LogEntry[]>("get_queue_log", { p_hospital_id: hospitalId, p_doctor_id: null, p_type: type || null, p_limit: 300 }),
    { hospitalId, tables: ["queue_events"], pollMs: 20000 },
  );

  const rows = (data ?? []).filter((e) => !actor || e.actor === actor);
  const actors = Array.from(new Set((data ?? []).map((e) => e.actor))).sort();

  return (
    <div>
      <PageBand
        tone="mint"
        title="Transparency log"
        lead="Every queue decision: who made it, why, and the words the patient saw. Append-only; nobody can edit it."
      >
      <div className="flex flex-wrap items-end gap-3">
        <label className="text-sm font-bold">
          Action{" "}
          <select value={type} onChange={(e) => setType(e.target.value)} className="ml-2 h-10 rounded-xl bg-white px-3 font-normal">
            <option value="">All</option>
            {Object.entries(EVENT_LABELS).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm font-bold">
          Who{" "}
          <select value={actor} onChange={(e) => setActor(e.target.value)} className="ml-2 h-10 rounded-xl bg-white px-3 font-normal">
            <option value="">Everyone</option>
            {actors.map((a) => (
              <option key={a}>{a}</option>
            ))}
          </select>
        </label>
      </div>
      </PageBand>
      <div className="p-4 sm:p-8">
      {error && <p className="text-red-700">{error}</p>}
      <div className="overflow-x-auto rounded-[20px] bg-white ring-1 ring-[var(--edge)]">
        <table className="w-full text-left text-sm">
          <thead className="bg-white text-[13px] text-[var(--muted)]">
            <tr>
              <th className="px-3 py-2">Time</th>
              <th className="px-3 py-2">Token</th>
              <th className="px-3 py-2">Action</th>
              <th className="px-3 py-2">Change</th>
              <th className="px-3 py-2">Who</th>
              <th className="px-3 py-2">Reason</th>
              <th className="px-3 py-2">Patient was told</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--rule)]">
            {rows.map((e) => (
              <tr key={e.id} className={e.type === "send_to_emergency" ? "bg-red-50" : ""}>
                <td className="px-3 py-2 text-[13px] tabular-nums">{hhmmss(e.created_at)}</td>
                <td className="whitespace-nowrap px-3 py-2 font-bold">{e.token ?? e.doctor_name ?? "—"}</td>
                <td className="px-3 py-2">{EVENT_LABELS[e.type] ?? e.type}</td>
                <td className="px-3 py-2 text-[#2b363d]">{describe(e)}</td>
                <td className="px-3 py-2">
                  {e.actor} <span className="text-xs text-[var(--muted)]">{e.actor_role}</span>
                </td>
                <td className="px-3 py-2">
                  {reasonLabel(e.reason_code)}
                  {e.note && <span className="block text-xs italic text-[var(--muted)]">“{e.note}”</span>}
                </td>
                <td className="px-3 py-2 text-[#2b363d]">{e.patient_message ?? ""}</td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr>
                <td colSpan={7} className="px-3 py-6 text-center text-[var(--muted)]">
                  No entries yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
    </div>
  );
}
