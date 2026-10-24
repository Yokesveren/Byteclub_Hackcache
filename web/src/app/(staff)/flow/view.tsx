"use client";

import { AnimatePresence, motion } from "motion/react";
import { useEffect, useMemo, useRef, useState } from "react";
import h01 from "../../../../public/graph/H01.json";
import h02 from "../../../../public/graph/H02.json";
import h03 from "../../../../public/graph/H03.json";
import { BandFigure, PageBand } from "@/components/PageBand";
import { useShell } from "@/components/StaffShell";
import { FlowSim } from "@/lib/flowSim";
import type { HospitalGraph, GraphArea } from "@/lib/graph";
import { hhmm } from "@/lib/format";
import { useLiveData } from "@/lib/hooks";
import { rpc } from "@/lib/supabase";

const MAPS: Record<string, HospitalGraph> = { H01: h01 as HospitalGraph, H02: h02 as HospitalGraph, H03: h03 as HospitalGraph };
const floorWord = (g: HospitalGraph, floor: number) => (g.floors.length > 1 ? (floor === 0 ? "ground" : floor === 1 ? "first" : `floor ${floor}`) : "");

interface Flow {
  now: string;
  window_min: number;
  here: Record<string, number>;
  connected: number;
  today: {
    journeys: number;
    completed: number;
    wrong_turns: number;
    wrong_turns_per_journey: number;
    median_entry_to_room_min: number | null;
  };
  wrong_turn_spots: { waypoint_id: string; count: number }[];
}

type Level = "empty" | "green" | "amber" | "red";

const LEVEL: Record<Level, { fill: string; ink: string; word: string }> = {
  empty: { fill: "#eef2f5", ink: "#56636b", word: "Quiet" },
  green: { fill: "#c9eed6", ink: "#0d3b1e", word: "Normal" },
  amber: { fill: "#ffe08a", ink: "#164e3f", word: "Busy" },
  red: { fill: "#ff9f97", ink: "#5c0b0f", word: "Overcrowded" },
};

function levelOf(n: number, capacity: number): Level {
  if (n === 0) return "empty";
  const r = n / capacity;
  return r >= 1 ? "red" : r >= 0.6 ? "amber" : "green";
}

export default function FlowView() {
  const { staff, hospitalId, now } = useShell();
  const g = MAPS[hospitalId];
  const { data, error, connected } = useLiveData(() => rpc<Flow>("get_flow", { p_hospital_id: hospitalId, p_window_min: 15 }), {
    hospitalId,
    events: ["flow_changed"],
    pollMs: 10000,
  });

  const nodeName = useMemo(() => new Map((g?.nodes ?? []).map((n) => [n.id, n.name.en])), [g]);
  const areaCounts = useMemo(() => {
    const here = data?.here ?? {};
    return new Map((g?.areas ?? []).map((a) => [a.id, a.waypoints.reduce((s, w) => s + (here[w] ?? 0), 0)]));
  }, [data, g]);

  if (!g) {
    return (
      <div>
        <PageBand tone="night" title="Patient flow" lead="The indoor map for this hospital has not been drawn yet." />
      </div>
    );
  }

  const busiest = [...g.areas]
    .map((a) => ({ a, n: areaCounts.get(a.id) ?? 0 }))
    .filter((x) => x.n > 0)
    .sort((x, y) => y.n / y.a.capacity - x.n / x.a.capacity);
  const top = busiest[0];
  const finding = top
    ? `Busiest right now: ${top.a.label}${top.a.floor > 0 ? `, ${floorWord(g, top.a.floor)} floor` : ""}, ${top.n} ${top.n === 1 ? "visitor" : "visitors"} for ${top.a.capacity} places (${LEVEL[levelOf(top.n, top.a.capacity)].word.toLowerCase()}).`
    : "Nobody connected is moving through the building right now.";
  const canSimulate = staff.role === "demo" || staff.role === "admin";

  return (
    <div>
      <PageBand
        tone="night"
        title="Patient flow"
        lead={finding}
        aside={
          <span>
            {connected ? "Live" : "Reconnecting"} · last {data?.window_min ?? 15} min · updated {data ? hhmm(data.now) : "never"}
            {error && ` · ${error}`}
          </span>
        }
      >
        <dl className="grid grid-cols-2 gap-x-8 gap-y-5 lg:grid-cols-4">
          <BandFigure label="In the building now" value={data?.connected ?? "–"} />
          <BandFigure
            label="Gate to room, median"
            value={data?.today.median_entry_to_room_min ?? "–"}
            unit={data?.today.median_entry_to_room_min != null ? "min" : undefined}
          />
          <BandFigure
            label="Wrong turns per journey"
            value={data ? data.today.wrong_turns_per_journey.toFixed(2) : "–"}
            note={data && data.today.journeys > 0 ? (data.today.wrong_turns_per_journey <= 1 ? "target ≤ 1 met" : "above target 1") : undefined}
          />
          <BandFigure label="Journeys today" value={data?.today.journeys ?? "–"} unit={data ? `${data.today.completed} finished` : undefined} />
        </dl>
      </PageBand>

      <div className="grid gap-6 p-4 sm:p-8 2xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-6">
          {g.floors.map((f) => (
            <FloorPlan key={f.floor} g={g} floor={f.floor} name={f.name} here={data?.here ?? {}} spots={data?.wrong_turn_spots ?? []} counts={areaCounts} />
          ))}
          <Legend connected={data?.connected ?? 0} />
        </div>

        <aside className="space-y-6">
          {canSimulate && <Simulator g={g} hospitalId={hospitalId} now={now} />}

          <section className="rounded-[24px] bg-white p-6 ring-1 ring-[var(--edge)]">
            <h2 className="text-xl font-black uppercase tracking-[-0.03em]">Busiest now</h2>
            {busiest.length === 0 ? (
              <p className="mt-3 text-[var(--muted)]">No connected visitors in the last 15 minutes.</p>
            ) : (
              <ul className="mt-4 space-y-3">
                {busiest.slice(0, 6).map(({ a, n }) => {
                  const lv = levelOf(n, a.capacity);
                  return (
                    <li key={a.id}>
                      <div className="flex items-baseline justify-between gap-3 text-sm">
                        <span className="font-bold">
                          {a.label}
                          {floorWord(g, a.floor) && <span className="font-normal text-[var(--muted)]"> · {floorWord(g, a.floor)}</span>}
                        </span>
                        <span className="tabular-nums">
                          {n} / {a.capacity} <span className="font-bold" style={{ color: LEVEL[lv].ink }}>{LEVEL[lv].word}</span>
                        </span>
                      </div>
                      <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-[#eef2f5]">
                        <motion.div
                          className="h-full rounded-full"
                          style={{ background: lv === "red" ? "#c4242a" : lv === "amber" ? "#d99a00" : "#2f8a55" }}
                          animate={{ width: `${Math.min(100, (n / a.capacity) * 100)}%` }}
                          transition={{ type: "spring", stiffness: 160, damping: 24 }}
                        />
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>

          <section className="rounded-[24px] bg-white p-6 ring-1 ring-[var(--edge)]">
            <h2 className="text-xl font-black uppercase tracking-[-0.03em]">Wrong-turn hotspots</h2>
            <p className="mt-1 text-sm text-[var(--muted)]">Today. Where people scan a sticker that is off their route: check the signs and stickers here.</p>
            {data && data.wrong_turn_spots.length > 0 ? (
              <ol className="mt-4 space-y-2">
                {data.wrong_turn_spots.map((s) => (
                  <li key={s.waypoint_id} className="flex items-center justify-between gap-3 rounded-xl bg-[#ffedea] px-3 py-2 text-sm">
                    <span className="font-bold text-[#831c1e]">{nodeName.get(s.waypoint_id) ?? s.waypoint_id}</span>
                    <span className="tabular-nums text-[#831c1e]">{s.count}×</span>
                  </li>
                ))}
              </ol>
            ) : (
              <p className="mt-3 text-[var(--muted)]">No wrong turns recorded today.</p>
            )}
          </section>
        </aside>
      </div>
    </div>
  );
}

function FloorPlan({
  g,
  floor,
  name,
  here,
  spots,
  counts,
}: {
  g: HospitalGraph;
  floor: number;
  name: string;
  here: Record<string, number>;
  spots: { waypoint_id: string; count: number }[];
  counts: Map<string, number>;
}) {
  const nodes = g.nodes.filter((n) => n.floor === floor);
  const byId = new Map(g.nodes.map((n) => [n.id, n]));
  const edges = g.edges.filter((e) => byId.get(e.from)!.floor === floor && byId.get(e.to)!.floor === floor);
  const areas = g.areas.filter((a) => a.floor === floor);
  const spotSet = new Map(spots.map((s) => [s.waypoint_id, s.count]));
  const total = nodes.reduce((s, n) => s + (here[n.id] ?? 0), 0);
  const plan = g.floors.find((f) => f.floor === floor);

  return (
    <section className="overflow-hidden rounded-[24px] bg-white ring-1 ring-[var(--edge)]">
      <header className="flex items-baseline justify-between gap-4 bg-[#0b2a3c] px-6 py-3 text-white">
        <h2 className="text-lg font-black uppercase tracking-[-0.02em]">{name}</h2>
        <span className="text-sm text-[#c9c9c9] tabular-nums">{total} connected {total === 1 ? "visitor" : "visitors"}</span>
      </header>
      <svg viewBox={`0 0 ${plan?.width ?? 1000} ${plan?.height ?? 560}`} className="block h-auto w-full" role="img" aria-label={`${name}: crowd levels by area`}>
        <rect width={plan?.width ?? 1000} height={plan?.height ?? 560} fill="#f8fafb" />
        {areas.map((a) => (
          <AreaShape key={a.id} a={a} n={counts.get(a.id) ?? 0} />
        ))}
        {edges.map((e, i) => {
          const a = byId.get(e.from)!;
          const b = byId.get(e.to)!;
          return <line key={i} x1={a.x} y1={a.y} x2={b.x} y2={b.y} stroke="#9aa7b0" strokeWidth={2} strokeDasharray={e.crowd_prone ? undefined : "4 4"} />;
        })}
        {nodes
          .filter((n) => n.sticker)
          .map((n) => (
            <g key={n.id}>
              {spotSet.has(n.id) && (
                <motion.circle
                  cx={n.x}
                  cy={n.y}
                  fill="none"
                  stroke="#c4242a"
                  strokeWidth={3}
                  strokeDasharray="5 4"
                  initial={{ r: 12 }}
                  animate={{ r: [16, 22, 16] }}
                  transition={{ duration: 2, repeat: Infinity }}
                />
              )}
              <rect x={n.x - 6} y={n.y - 6} width={12} height={12} rx={2} fill={n.tags.includes("emergency") ? "#c4242a" : "#0b2a3c"}>
                <title>{n.name.en}</title>
              </rect>
            </g>
          ))}
        <AnimatePresence>
          {nodes
            .filter((n) => (here[n.id] ?? 0) > 0)
            .map((n) => {
              const c = here[n.id];
              const r = Math.min(30, 11 + Math.sqrt(c) * 5);
              return (
                <motion.g
                  key={n.id}
                  initial={{ opacity: 0, scale: 0.4 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.4 }}
                  transition={{ type: "spring", stiffness: 300, damping: 20 }}
                  style={{ transformOrigin: `${n.x}px ${n.y - 22}px` }}
                >
                  <motion.circle cx={n.x} cy={n.y - 22} fill="#0b7a75" animate={{ r }} transition={{ type: "spring", stiffness: 260, damping: 18 }} />
                  <text x={n.x} y={n.y - 17} textAnchor="middle" fontSize={15} fontWeight={900} fill="#ffffff">
                    {c}
                  </text>
                </motion.g>
              );
            })}
        </AnimatePresence>
      </svg>
    </section>
  );
}

function AreaShape({ a, n }: { a: GraphArea; n: number }) {
  const lv = levelOf(n, a.capacity);
  const style = LEVEL[lv];
  const isRoomLike = a.kind !== "corridor";
  return (
    <g>
      <motion.rect
        x={a.x}
        y={a.y}
        width={a.w}
        height={a.h}
        rx={a.kind === "corridor" ? 10 : 14}
        initial={false}
        animate={{ fill: a.kind === "emergency" && lv === "empty" ? "#fde2e0" : style.fill }}
        transition={{ duration: 0.6 }}
        stroke="#ffffff"
        strokeWidth={4}
      />
      {isRoomLike && (
        <>
          <text x={a.x + 12} y={a.y + 26} fontSize={a.w < 90 ? 13 : 16} fontWeight={800} fill="#0b2a3c">
            {a.label}
          </text>
          {a.h > 120 && (
            <text x={a.x + 12} y={a.y + 46} fontSize={13} fill={style.ink} fontWeight={700}>
              {n > 0 ? `${n}/${a.capacity} · ${style.word}` : style.word}
            </text>
          )}
        </>
      )}
    </g>
  );
}

function Legend({ connected }: { connected: number }) {
  return (
    <div className="flex flex-wrap items-center gap-x-6 gap-y-2 rounded-[20px] bg-[#edf3f8] px-5 py-4 text-sm">
      {(["green", "amber", "red"] as Level[]).map((l) => (
        <span key={l} className="flex items-center gap-2">
          <span className="h-4 w-6 rounded" style={{ background: LEVEL[l].fill }} />
          <strong>{LEVEL[l].word}</strong>
          <span className="text-[#3e5566]">{l === "green" ? "under 60% of places" : l === "amber" ? "60–99%" : "full or over"}</span>
        </span>
      ))}
      <span className="flex items-center gap-2">
        <span className="grid h-5 w-5 place-items-center rounded-full bg-[#0b7a75] text-[10px] font-black text-white">3</span> people at a sticker
      </span>
      <span className="flex items-center gap-2">
        <span className="h-4 w-4 rounded-full border-2 border-dashed border-[#c4242a]" /> wrong-turn hotspot
      </span>
      <span className="basis-full text-[#3e5566]">
        Based on {connected} connected {connected === 1 ? "visitor" : "visitors"} who agreed to anonymous movement tracking. A flow indicator, not a head-count.
      </span>
    </div>
  );
}

/** Demo only: simulated visitors walking real routes, sending real scan events. */
function Simulator({ g, hospitalId, now }: { g: HospitalGraph; hospitalId: string; now: number }) {
  const [on, setOn] = useState(false);
  const [target, setTarget] = useState(70);
  const [walking, setWalking] = useState(0);
  const [sent, setSent] = useState(0);
  const sim = useRef<FlowSim | null>(null);
  const nowRef = useRef(now);
  const targetRef = useRef(target);

  useEffect(() => {
    nowRef.current = now;
  }, [now]);
  useEffect(() => {
    targetRef.current = target;
  }, [target]);

  useEffect(() => {
    if (!on) return;
    sim.current ??= new FlowSim(g);
    let alive = true;
    const send = async (events: ReturnType<FlowSim["tick"]>) => {
      if (!events.length) return;
      try {
        await rpc<number>("ingest_journey_events", { p_events: events });
        if (alive) setSent((s) => s + events.length);
      } catch {
        /* the next tick retries with fresh events */
      }
    };
    const t0 = nowRef.current || Date.now();
    if (sim.current.count === 0) void send(sim.current.warmStart(Math.round(targetRef.current * 0.7), t0, hospitalId));
    const id = setInterval(() => {
      const s = sim.current!;
      void send(s.tick(nowRef.current || Date.now(), hospitalId, targetRef.current));
      setWalking(s.count);
    }, 1500);
    return () => {
      alive = false;
      clearInterval(id);
    };
  }, [on, g, hospitalId]);

  return (
    <section className="rounded-[24px] bg-[#0b7a75] p-6 text-white">
      <h2 className="text-xl font-black uppercase tracking-[-0.03em]">Simulate visitors</h2>
      <p className="mt-1 text-sm text-[#c9ece6]">
        Demo only. Simulated patients walk real routes through the stickers and send real scan events. Runs while this page is open.
      </p>
      <div className="mt-4 flex items-center gap-3">
        <motion.button
          whileTap={{ scale: 0.96 }}
          onClick={() => setOn((v) => !v)}
          aria-pressed={on}
          className={`h-12 rounded-xl px-5 font-black uppercase ${on ? "bg-white text-[#0b2a3c]" : "bg-[#9fe5cf] text-[#0b2a3c]"}`}
        >
          {on ? "Stop" : "Start"}
        </motion.button>
        <span className="text-sm tabular-nums">
          {on ? `${walking} walking · ${sent} scans sent` : sent ? `stopped · ${sent} scans sent` : "not running"}
        </span>
      </div>
      <label className="mt-5 block text-sm font-bold">
        People in the building: {target}
        <input
          type="range"
          min={10}
          max={160}
          step={10}
          value={target}
          onChange={(e) => setTarget(Number(e.target.value))}
          className="mt-2 w-full accent-[#9fe5cf]"
        />
      </label>
    </section>
  );
}
