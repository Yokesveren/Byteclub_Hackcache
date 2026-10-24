"use client";

import { motion } from "motion/react";
import Link from "next/link";
import { useState } from "react";
import { BandFigure, PageBand } from "@/components/PageBand";
import { useShell } from "@/components/StaffShell";
import { hhmm } from "@/lib/format";
import { useLiveData } from "@/lib/hooks";
import { rpc } from "@/lib/supabase";

interface Hour {
  hour: number;
  mid: number;
  lo: number;
  hi: number;
}

interface District {
  now: string;
  tomorrow: string;
  hospitals: {
    id: string;
    name: string;
    short_name: string;
    district: string;
    services: Record<string, string>;
    waiting: number;
    seen: number;
    doctors_in: number;
    doctors_total: number;
    avg_waited_min: number | null;
    open_flags: number;
    departments: { id: string; code: string; name: string; waiting: number; level: "green" | "amber" | "red" }[];
  }[];
  balancing: { from: string; to: string; department: string; from_waiting: number; to_waiting: number; km: number }[];
  forecast: { hospital_id: string; short_name: string; capacity_per_hour: number; hours: Hour[] | null; total: number | null }[];
  outbreak: {
    signals: { area: string; cluster: string; today: number; usual: number; sd: number; z: number | null }[];
    watched: number;
    suppressed: number;
  };
  followups: { due: number; done: number; defaulted: number };
  impact: { patients: number; avg_wait_min: number | null; baseline_wait_min: number | null; hours_saved: number | null };
}

const LEVEL_WORD = { green: "Normal", amber: "Busy", red: "Overcrowded" } as const;
const LEVEL_DOT = { green: "#2f8a55", amber: "#d99a00", red: "#c4242a" } as const;
const SERVICE_WORD: Record<string, string> = { up: "running", down: "down", low: "low stock" };
const CLUSTER_WORD: Record<string, string> = {
  fever: "Fever",
  respiratory: "Cough and breathing",
  diarrhoea: "Loose motions",
  skin: "Skin and rash",
  injury: "Injuries",
};
// Validated with the dataviz palette checker against the white card: all six checks pass.
const BAR = "#3b78b0";
const OVER = "#c4242a";

export default function DistrictView() {
  const { hospitalId } = useShell();
  const { data, error, connected } = useLiveData(() => rpc<District>("get_district"), {
    hospitalId,
    tables: ["visits", "urgent_flags", "doctors"],
    pollMs: 20000,
  });

  if (!data) return <div className="p-8 text-[var(--muted)]">{error ? `Could not load: ${error}` : "Gathering the district…"}</div>;
  const waiting = data.hospitals.reduce((s, h) => s + h.waiting, 0);
  const flags = data.hospitals.reduce((s, h) => s + h.open_flags, 0);
  const forecastDay = new Date(`${data.tomorrow}T00:00:00`).toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long" });

  return (
    <div>
      <PageBand
        tone="mint"
        title="District overview"
        lead={
          <>
            <strong>Anantnag district</strong> · three connected hospitals (demo).{" "}
            {data.balancing.length > 0
              ? `${data.balancing[0].from} ${data.balancing[0].department} is overcrowded while ${data.balancing[0].to} has room.`
              : "Every department is within its load."}
          </>
        }
        aside={
          <span>
            {connected ? "Live" : "Reconnecting"} · updated {hhmm(data.now)}
          </span>
        }
      >
        <dl className="grid grid-cols-2 gap-x-8 gap-y-5 lg:grid-cols-5">
          <BandFigure label="Patient-hours saved today" value={data.impact.hours_saved ?? 0} unit="h" />
          <BandFigure
            label="Average wait today"
            value={data.impact.avg_wait_min ?? "–"}
            unit={data.impact.baseline_wait_min ? `min, was ${data.impact.baseline_wait_min}` : "min"}
          />
          <BandFigure label="Patients seen today" value={data.impact.patients} />
          <BandFigure label="Waiting now" value={waiting} />
          <BandFigure label="Urgent requests open" value={flags} note={flags > 0 ? "go to facility" : undefined} />
        </dl>
        <p className="mt-4 text-sm" style={{ color: "var(--band-soft)" }}>
          Hours saved = each patient called today, against the pre-SevaSetu baseline wait of {data.impact.baseline_wait_min ?? 120} minutes. Simulated data.
        </p>
      </PageBand>

      <div className="space-y-8 p-4 sm:p-8">
        {/* Hospitals side by side */}
        <section>
          <h2 className="text-2xl font-black uppercase tracking-[-0.03em]">Hospitals now</h2>
          <div className="mt-4 grid gap-4 lg:grid-cols-3">
            {data.hospitals.map((h, i) => {
              const worst = h.departments.reduce<"green" | "amber" | "red">(
                (w, d) => (d.level === "red" || w === "red" ? "red" : d.level === "amber" || w === "amber" ? "amber" : "green"),
                "green",
              );
              return (
                <motion.article
                  key={h.id}
                  initial={{ opacity: 0, y: 24 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.08, type: "spring", stiffness: 200, damping: 24 }}
                  className="overflow-hidden rounded-[24px] bg-white ring-1 ring-[var(--edge)]"
                >
                  <header className="flex items-start justify-between gap-3 bg-[#0b2a3c] p-5 text-white">
                    <div>
                      <h3 className="text-xl font-black uppercase leading-tight tracking-[-0.03em]">{h.short_name}</h3>
                      <p className="text-sm text-[#b4b4b4]">{h.name.replace(" (Demo)", "")}</p>
                    </div>
                    <span className="flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-sm font-bold">
                      <span className="h-2.5 w-2.5 rounded-full" style={{ background: LEVEL_DOT[worst] }} />
                      {LEVEL_WORD[worst]}
                    </span>
                  </header>
                  <dl className="grid grid-cols-3 gap-3 p-5">
                    <Mini label="Waiting" value={h.waiting} />
                    <Mini label="Doctors in" value={`${h.doctors_in}/${h.doctors_total}`} />
                    <Mini label="Avg waited" value={h.avg_waited_min ?? "–"} unit="min" />
                  </dl>
                  <ul className="space-y-2 px-5 pb-4">
                    {h.departments.map((d) => (
                      <li key={d.id} className="flex items-center justify-between gap-3 text-sm">
                        <span className="flex items-center gap-2">
                          <span className="h-2.5 w-2.5 rounded-full" style={{ background: LEVEL_DOT[d.level] }} />
                          {d.name}
                        </span>
                        <span className="tabular-nums">
                          {d.waiting} waiting · <strong>{LEVEL_WORD[d.level]}</strong>
                        </span>
                      </li>
                    ))}
                  </ul>
                  <div className="flex flex-wrap gap-2 border-t border-[var(--rule)] px-5 py-3">
                    {Object.entries(h.services).map(([k, v]) => (
                      <span
                        key={k}
                        className={`rounded-full px-2.5 py-1 text-xs font-bold ${
                          v === "up" ? "bg-[#dcf5e4] text-[#0d3b1e]" : v === "down" ? "bg-[#ffedea] text-[#831c1e]" : "bg-[#fff1c2] text-[#5c3d00]"
                        }`}
                      >
                        {k === "xray" ? "X-ray" : k[0].toUpperCase() + k.slice(1)} {SERVICE_WORD[v] ?? v}
                      </span>
                    ))}
                    {h.open_flags > 0 && <span className="rounded-full bg-[#c4242a] px-2.5 py-1 text-xs font-bold text-white">{h.open_flags} urgent open</span>}
                  </div>
                </motion.article>
              );
            })}
          </div>
        </section>

        {/* Load balancing (13.7) */}
        {data.balancing.length > 0 && (
          <section className="rounded-[24px] bg-[#dcecf7] p-6">
            <h2 className="text-2xl font-black uppercase tracking-[-0.03em]">Share the load</h2>
            <ul className="mt-4 space-y-3">
              {data.balancing.map((b, i) => (
                <li key={i} className="flex flex-wrap items-center gap-4 rounded-2xl bg-white p-4">
                  <span className="text-lg font-black">{b.from}</span>
                  <span className="text-sm">
                    {b.department}: {b.from_waiting} waiting
                  </span>
                  <span className="text-2xl font-black text-[#0b7a75]" aria-hidden>
                    →
                  </span>
                  <span className="text-lg font-black">{b.to}</span>
                  <span className="text-sm">
                    {b.to_waiting} waiting · {b.km} km away
                  </span>
                </li>
              ))}
            </ul>
            <p className="mt-3 text-sm">
              Suggest sending new <strong>routine, remote</strong> registrations to the quieter hospital. Patients see the reason and the distance and
              may decline. Flagged, Priority and Emergency patients are never redirected (S6).
            </p>
          </section>
        )}

        {/* Forecast (13.2) */}
        <section className="rounded-[24px] bg-white p-6 ring-1 ring-[var(--edge)]">
          <div className="flex flex-wrap items-baseline justify-between gap-3">
            <h2 className="text-2xl font-black uppercase tracking-[-0.03em]">Expected patients, {forecastDay}</h2>
            <p className="text-sm text-[var(--muted)]">Same weekday over the last six weeks, recent weeks weighted more. Bar = expected, whisker = likely range.</p>
          </div>
          <div className="mt-6 grid gap-6 lg:grid-cols-3">
            {data.forecast.map((f) => (
              <ForecastPanel key={f.hospital_id} f={f} />
            ))}
          </div>
          <details className="mt-5 text-sm">
            <summary className="cursor-pointer font-bold">Show the numbers</summary>
            <div className="mt-3 overflow-x-auto">
              <table className="w-full text-left tabular-nums">
                <thead className="text-[var(--muted)]">
                  <tr>
                    <th className="py-1 pr-4 font-normal">Hospital</th>
                    {data.forecast[0]?.hours?.map((h) => (
                      <th key={h.hour} className="py-1 pr-4 text-right font-normal">
                        {h.hour}:00
                      </th>
                    ))}
                    <th className="py-1 text-right font-normal">Capacity / h</th>
                  </tr>
                </thead>
                <tbody>
                  {data.forecast.map((f) => (
                    <tr key={f.hospital_id} className="border-t border-[var(--rule)]">
                      <td className="py-1.5 pr-4 font-bold">{f.short_name}</td>
                      {f.hours?.map((h) => (
                        <td key={h.hour} className="py-1.5 pr-4 text-right">
                          {h.mid} <span className="text-[var(--muted)]">({h.lo}–{h.hi})</span>
                        </td>
                      ))}
                      <td className="py-1.5 text-right">{f.capacity_per_hour}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
        </section>

        <div className="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
          {/* Outbreak early warning (13.6) */}
          <section className="rounded-[24px] bg-[#0b2a3c] p-6 text-white">
            <h2 className="text-2xl font-black uppercase tracking-[-0.03em]">Outbreak early warning</h2>
            <p className="mt-1 text-sm text-[#b4b4b4]">
              Symptom clusters by area today, against the area’s last four weeks. Anonymous counts only; areas with fewer than five cases are not shown (
              {data.outbreak.suppressed} hidden).
            </p>
            {data.outbreak.signals.length === 0 ? (
              <p className="mt-5 text-lg">No cluster is above its usual range today.</p>
            ) : (
              <ul className="mt-5 space-y-3">
                {data.outbreak.signals.map((s) => (
                  <motion.li
                    key={`${s.area}-${s.cluster}`}
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    className="flex flex-wrap items-center justify-between gap-4 rounded-2xl bg-[#153e53] p-4"
                  >
                    <div>
                      <p className="text-xl font-black">
                        {CLUSTER_WORD[s.cluster] ?? s.cluster} · {s.area}
                      </p>
                      <p className="text-sm text-[#c9c9c9]">
                        {s.today} cases today · usually {s.usual} a day (±{s.sd})
                      </p>
                    </div>
                    <span className="rounded-full bg-[#9fe5cf] px-3 py-1.5 text-sm font-black text-[#0b2a3c]">▲ Review advised</span>
                  </motion.li>
                ))}
              </ul>
            )}
            <p className="mt-4 text-xs text-[#9a9a9a]">
              Advisory only. SevaSetu does not declare an outbreak: the district health officer reviews the signal and hands it to disease surveillance.
            </p>
          </section>

          {/* Follow-ups (11.2) */}
          <section className="rounded-[24px] bg-[#edf3f8] p-6">
            <h2 className="text-2xl font-black uppercase tracking-[-0.03em]">Follow-ups</h2>
            <dl className="mt-4 grid grid-cols-3 gap-3">
              <Mini label="Missed" value={data.followups.defaulted} tone="bad" />
              <Mini label="Due soon" value={data.followups.due} />
              <Mini label="Came back" value={data.followups.done} />
            </dl>
            <p className="mt-4 text-sm">Patients who miss a follow-up are called or sent an SMS by the hospital team. Every contact is logged.</p>
            <Link href="/followups" className="mt-4 inline-flex h-11 items-center rounded-xl bg-[#0b2a3c] px-5 font-bold text-white hover:bg-[#1c4a60]">
              Open follow-ups →
            </Link>
          </section>
        </div>
      </div>
    </div>
  );
}

function Mini({ label, value, unit, tone }: { label: string; value: number | string; unit?: string; tone?: "bad" }) {
  return (
    <div>
      <dt className="text-xs text-[var(--muted)]">{label}</dt>
      <dd className={`mt-0.5 text-2xl font-black tabular-nums tracking-[-0.03em] ${tone === "bad" && Number(value) > 0 ? "text-[#a3161c]" : ""}`}>
        {value}
        {unit && <span className="ml-1 text-sm font-bold tracking-normal">{unit}</span>}
      </dd>
    </div>
  );
}

/** One hospital's expected patients per hour: bars with a likely-range whisker, capacity as a reference line. */
function ForecastPanel({ f }: { f: District["forecast"][number] }) {
  const [hover, setHover] = useState<Hour | null>(null);
  if (!f.hours?.length) return <p className="text-[var(--muted)]">{f.short_name}: not enough history yet.</p>;
  const W = 340;
  const H = 210;
  const pad = { l: 34, r: 8, t: 22, b: 26 };
  // Round the axis up to a tick above the highest whisker or the capacity line, so nothing is clipped.
  const max = Math.max(f.capacity_per_hour, ...f.hours.map((h) => h.hi));
  const step = max <= 40 ? 10 : max <= 100 ? 20 : 40;
  const ticks = [0];
  while (ticks[ticks.length - 1] < max * 1.08) ticks.push(ticks[ticks.length - 1] + step);
  const top = ticks[ticks.length - 1];
  const y = (v: number) => pad.t + (H - pad.t - pad.b) * (1 - v / top);
  const band = (W - pad.l - pad.r) / f.hours.length;
  const barW = Math.min(24, band * 0.55);
  const over = f.hours.filter((h) => h.mid > f.capacity_per_hour);

  return (
    <figure className="m-0">
      <figcaption className="flex items-baseline justify-between gap-2">
        <span className="font-black uppercase tracking-[-0.02em]">{f.short_name}</span>
        <span className="text-sm text-[var(--muted)] tabular-nums">{f.total} expected</span>
      </figcaption>
      <div className="relative mt-2">
        <svg viewBox={`0 0 ${W} ${H}`} className="block h-auto w-full" role="img" aria-label={`${f.short_name}: expected patients per hour, capacity ${f.capacity_per_hour} per hour`}>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={pad.l} x2={W - pad.r} y1={y(t)} y2={y(t)} stroke="#eef2f5" />
              <text x={pad.l - 6} y={y(t) + 4} textAnchor="end" fontSize={11} fill="#56636b">
                {t}
              </text>
            </g>
          ))}
          {f.hours.map((h, i) => {
            const cx = pad.l + band * i + band / 2;
            const isOver = h.mid > f.capacity_per_hour;
            const barTop = y(h.mid);
            const base = y(0);
            return (
              <g key={h.hour} onMouseEnter={() => setHover(h)} onMouseLeave={() => setHover(null)}>
                <rect x={cx - band / 2} y={pad.t} width={band} height={H - pad.t - pad.b} fill="transparent" />
                <motion.path
                  initial={{ opacity: 0 }}
                  animate={{ opacity: hover && hover.hour !== h.hour ? 0.45 : 1 }}
                  d={`M${cx - barW / 2},${base} V${barTop + 4} q0,-4 4,-4 h${barW - 8} q4,0 4,4 V${base} Z`}
                  fill={isOver ? OVER : BAR}
                />
                <line x1={cx} x2={cx} y1={y(h.hi)} y2={y(h.lo)} stroke="#0b2a3c" strokeWidth={2} />
                <line x1={cx - 5} x2={cx + 5} y1={y(h.hi)} y2={y(h.hi)} stroke="#0b2a3c" strokeWidth={2} />
                <line x1={cx - 5} x2={cx + 5} y1={y(h.lo)} y2={y(h.lo)} stroke="#0b2a3c" strokeWidth={2} />
                {isOver && (
                  <text x={cx} y={y(h.hi) - 6} textAnchor="middle" fontSize={12} fontWeight={900} fill="#0b2a3c">
                    ▲
                  </text>
                )}
                <text x={cx} y={H - 8} textAnchor="middle" fontSize={11} fill="#56636b">
                  {h.hour}
                </text>
              </g>
            );
          })}
          <line x1={pad.l} x2={W - pad.r} y1={y(f.capacity_per_hour)} y2={y(f.capacity_per_hour)} stroke="#0b2a3c" strokeWidth={2} strokeDasharray="6 4" />
          <text x={W - pad.r} y={y(f.capacity_per_hour) - 6} textAnchor="end" fontSize={11} fontWeight={700} fill="#0b2a3c">
            capacity {f.capacity_per_hour}/h
          </text>
        </svg>
        {hover && (
          <div className="pointer-events-none absolute left-1/2 top-0 -translate-x-1/2 rounded-lg bg-[#0b2a3c] px-3 py-2 text-xs text-white shadow-lg">
            <strong>{hover.hour}:00–{hover.hour + 1}:00</strong> · expected {hover.mid} (likely {hover.lo}–{hover.hi}) · capacity {f.capacity_per_hour}
          </div>
        )}
      </div>
      <p className={`mt-2 text-sm ${over.length ? "font-bold text-[#a3161c]" : "text-[var(--muted)]"}`}>
        {over.length
          ? `▲ Over capacity at ${over.map((h) => `${h.hour}:00`).join(", ")}: consider another doctor or a staggered start.`
          : "Within capacity all day."}
      </p>
    </figure>
  );
}
