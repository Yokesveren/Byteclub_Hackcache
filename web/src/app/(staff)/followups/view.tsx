"use client";

import { AnimatePresence, motion } from "motion/react";
import { useState } from "react";
import { BandChip } from "@/components/BandChip";
import { BandFigure, PageBand } from "@/components/PageBand";
import { useShell } from "@/components/StaffShell";
import { hhmm, LANGUAGE_NAMES } from "@/lib/format";
import { useLiveData } from "@/lib/hooks";
import { rpc } from "@/lib/supabase";
import type { Band } from "@/lib/types";

interface FollowUp {
  journey_id: string;
  first_name: string;
  age_group: string;
  language: string;
  phone: string | null;
  status: Band;
  follow_up_on: string;
  consult_ok: boolean;
  doctor: string | null;
  department: string;
  last_visit: string;
  state: "due" | "defaulted" | "done";
  days_late: number;
  actions: { action: "called" | "sms" | "community"; actor: string; at: string; note: string | null }[];
}

type Filter = "defaulted" | "due" | "done" | "all";
const FILTERS: { id: Filter; label: string }[] = [
  { id: "defaulted", label: "Missed" },
  { id: "due", label: "Due soon" },
  { id: "done", label: "Came back" },
  { id: "all", label: "All" },
];
const ACTIONS = [
  { id: "called", label: "Called", done: "Called" },
  { id: "sms", label: "Send SMS reminder", done: "SMS sent" },
  { id: "community", label: "Flag for community follow-up", done: "Flagged for community follow-up" },
] as const;
const day = (d: string) => new Date(`${d}T00:00:00`).toLocaleDateString("en-IN", { day: "numeric", month: "short" });

export default function FollowupsView() {
  const { hospitalId } = useShell();
  const [filter, setFilter] = useState<Filter>("defaulted");
  const [busy, setBusy] = useState<string | null>(null);
  const { data, error, refresh } = useLiveData(() => rpc<FollowUp[]>("get_followups", { p_hospital_id: hospitalId }), {
    hospitalId,
    tables: ["visits"],
    pollMs: 30000,
  });

  if (!data) return <div className="p-8 text-[var(--muted)]">{error ? `Could not load: ${error}` : "Loading follow-ups…"}</div>;
  const count = (f: Filter) => (f === "all" ? data.length : data.filter((x) => x.state === f).length);
  const rows = data.filter((x) => filter === "all" || x.state === filter);

  const act = async (id: string, action: string) => {
    setBusy(`${id}:${action}`);
    try {
      await rpc("followup_action", { p: { journey_id: id, action } });
      await refresh();
    } finally {
      setBusy(null);
    }
  };

  return (
    <div>
      <PageBand
        tone="sky"
        title="Follow-ups"
        lead={
          count("defaulted") > 0
            ? `${count("defaulted")} ${count("defaulted") === 1 ? "patient has" : "patients have"} missed a follow-up the doctor asked for. Call or message them, and log what happened.`
            : "Nobody has missed a follow-up."
        }
      >
        <dl className="grid grid-cols-3 gap-x-8 sm:max-w-xl">
          <BandFigure label="Missed" value={count("defaulted")} />
          <BandFigure label="Due soon" value={count("due")} />
          <BandFigure label="Came back" value={count("done")} />
        </dl>
        <div className="mt-6 flex flex-wrap gap-2" role="tablist" aria-label="Show">
          {FILTERS.map((f) => (
            <button
              key={f.id}
              role="tab"
              aria-selected={filter === f.id}
              onClick={() => setFilter(f.id)}
              className={`relative h-11 rounded-full px-4 text-sm font-bold ${filter === f.id ? "text-white" : "bg-white/55 hover:bg-white/85"}`}
            >
              {filter === f.id && <motion.span layoutId="fu-pill" className="absolute inset-0 rounded-full bg-[#0a0a0a]" transition={{ type: "spring", stiffness: 420, damping: 34 }} />}
              <span className="relative">
                {f.label} <span className="opacity-75 tabular-nums">{count(f.id)}</span>
              </span>
            </button>
          ))}
        </div>
      </PageBand>

      <div className="grid gap-4 p-4 sm:p-8 xl:grid-cols-2">
        <AnimatePresence mode="popLayout">
          {rows.map((r) => (
            <motion.article
              key={r.journey_id}
              layout
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.97 }}
              transition={{ type: "spring", stiffness: 260, damping: 26 }}
              className="overflow-hidden rounded-[24px] bg-white ring-1 ring-[var(--edge)]"
            >
              <header
                className={`flex flex-wrap items-start justify-between gap-3 p-5 ${
                  r.state === "defaulted" ? "bg-[#ffedea]" : r.state === "due" ? "bg-[#fff1c2]" : "bg-[#dcf5e4]"
                }`}
              >
                <div>
                  <h2 className="text-2xl font-black uppercase tracking-[-0.03em]">{r.first_name}</h2>
                  <p className="text-sm">
                    {r.age_group} · speaks {LANGUAGE_NAMES[r.language] ?? r.language}
                    {r.phone && ` · phone ${r.phone}`}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold">
                    {r.state === "defaulted" ? `Missed · ${r.days_late} ${r.days_late === 1 ? "day" : "days"} late` : r.state === "due" ? "Due" : "Came back"}
                  </p>
                  <p className="text-lg font-black tabular-nums">{day(r.follow_up_on)}</p>
                </div>
              </header>
              <div className="space-y-3 p-5">
                <p className="flex flex-wrap items-center gap-2 text-sm">
                  Last seen {day(r.last_visit)} by {r.doctor ?? "a doctor"}, {r.department} · <BandChip band={r.status} />
                  {r.consult_ok && <span className="rounded-full bg-[#cbe4f2] px-2 py-0.5 text-xs font-bold">Text consult suitable</span>}
                </p>
                {r.state !== "done" && (
                  <div className="flex flex-wrap gap-2">
                    {ACTIONS.map((a) => (
                      <motion.button
                        key={a.id}
                        whileTap={{ scale: 0.96 }}
                        disabled={busy !== null || (a.id !== "called" && a.id === "sms" && !r.phone)}
                        onClick={() => act(r.journey_id, a.id)}
                        className={`h-10 rounded-xl px-3 text-sm font-bold disabled:opacity-40 ${
                          a.id === "called" ? "bg-[#b0298f] text-white hover:bg-[#951f78]" : "bg-[#f1f4f6] hover:bg-[#cbe4f2]"
                        }`}
                      >
                        {busy === `${r.journey_id}:${a.id}` ? "Saving…" : a.label}
                      </motion.button>
                    ))}
                  </div>
                )}
                {r.actions.length > 0 && (
                  <ol className="space-y-1 border-t border-[var(--rule)] pt-3 text-sm">
                    {r.actions.map((a, i) => (
                      <li key={i} className="flex justify-between gap-3">
                        <span>
                          <strong>{ACTIONS.find((x) => x.id === a.action)?.done}</strong> by {a.actor}
                        </span>
                        <span className="tabular-nums text-[var(--muted)]">{hhmm(a.at)}</span>
                      </li>
                    ))}
                  </ol>
                )}
              </div>
            </motion.article>
          ))}
        </AnimatePresence>
        {rows.length === 0 && <p className="text-lg text-[var(--muted)]">Nothing in this list.</p>}
      </div>
      <p className="px-4 pb-8 text-sm text-[var(--muted)] sm:px-8">
        Reminders go out by app and SMS two days before each follow-up. Community outreach by ASHA workers is on the roadmap; for now it is a staff call or SMS.
      </p>
    </div>
  );
}
