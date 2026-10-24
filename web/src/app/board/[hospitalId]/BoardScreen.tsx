"use client";

import { AnimatePresence, motion, MotionConfig } from "motion/react";
import { useCallback, useRef, useState } from "react";
import { floorName, hhmm } from "@/lib/format";
import { useAppClock, useLiveData, useRealNow } from "@/lib/hooks";
import { rpc } from "@/lib/supabase";
import type { Board, BoardDoctor } from "@/lib/types";

const CALL_HIGHLIGHT_MS = 20000;

function playChime(ctx: AudioContext) {
  [523, 659, 784].forEach((f, i) => {
    const o = ctx.createOscillator();
    const g = ctx.createGain();
    const t = ctx.currentTime + i * 0.25;
    o.frequency.value = f;
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(0.3, t + 0.03);
    g.gain.exponentialRampToValueAtTime(0.0001, t + 0.6);
    o.connect(g).connect(ctx.destination);
    o.start(t);
    o.stop(t + 0.65);
  });
}

/** Public waiting-room screen (12.1). Token and first name only (S9). */
export function BoardScreen({ hospitalId }: { hospitalId: string }) {
  const [calls, setCalls] = useState<Record<string, number>>({});
  const [latest, setLatest] = useState<{ token: string; name: string; room: string } | null>(null);
  const [chime, setChime] = useState(false);
  const previous = useRef<Record<string, string> | null>(null);
  const audio = useRef<AudioContext | null>(null);

  // Detect new calls (a new token, or the same token called again) as data arrives.
  const load = useCallback(async () => {
    const board = await rpc<Board>("get_board", { p_hospital_id: hospitalId });
    const prev = previous.current;
    const fresh: BoardDoctor[] = [];
    const next: Record<string, string> = {};
    for (const d of board.doctors) {
      const key = d.serving ? `${d.serving.token}#${d.serving.call_count}` : "";
      next[d.doctor_id] = key;
      if (prev && key && prev[d.doctor_id] !== key) fresh.push(d);
    }
    previous.current = next;
    if (fresh.length > 0) {
      const at = Date.now();
      setCalls((c) => ({ ...c, ...Object.fromEntries(fresh.map((d) => [d.doctor_id, at])) }));
      const last = fresh[fresh.length - 1];
      setLatest({ token: last.serving!.token, name: last.serving!.first_name, room: last.room });
      if (audio.current) playChime(audio.current);
    }
    return board;
  }, [hospitalId]);

  const { data, connected } = useLiveData(load, { hospitalId, pollMs: 10000 });
  const { now } = useAppClock();
  const realNow = useRealNow();

  const isCalling = (id: string) => calls[id] !== undefined && realNow - calls[id] < CALL_HIGHLIGHT_MS;

  return (
    <MotionConfig reducedMotion="user">
    <div className="min-h-screen bg-[#0b2a3c] pb-20 text-white">
      <header className="flex items-center justify-between gap-4 border-b border-[#19455a] px-6 py-4">
        <div>
          <h1 className="text-4xl font-black uppercase tracking-[-0.04em]">{data?.hospital.name ?? "Waiting room"}</h1>
          <p className="text-lg text-[#b4b4b4]">OPD · Now serving</p>
        </div>
        <div className="flex items-center gap-4">
          {!chime && (
            <button
              onClick={() => {
                audio.current ??= new AudioContext();
                void audio.current.resume();
                setChime(true);
              }}
              className="rounded border border-[#2b5e75] px-3 py-1 text-sm text-[#b4b4b4]"
            >
              Turn on call chime
            </button>
          )}
          <span className="text-4xl tabular-nums">{now ? hhmm(now) : "--:--"}</span>
          {!connected && <span className="text-sm text-amber-300">reconnecting…</span>}
        </div>
      </header>

      {latest && realNow - Math.max(...Object.values(calls)) < CALL_HIGHLIGHT_MS && (
        <div className="board-call px-6 py-4 text-center text-4xl font-black" role="status" aria-live="assertive">
          {latest.token} · {latest.name} → {latest.room}
        </div>
      )}

      <main className="grid gap-4 p-6 md:grid-cols-2 xl:grid-cols-3">
        {data?.doctors.map((d) => (
          <section
            key={d.doctor_id}
            className={`rounded-[24px] border-4 bg-[#103447] p-6 ${isCalling(d.doctor_id) ? "border-[#9fe5cf]" : "border-[#19455a]"}`}
          >
            <div className="flex items-baseline justify-between gap-2">
              <h2 className="text-5xl font-black uppercase tracking-[-0.04em]">{d.room}</h2>
              <span className="text-lg text-[#b4b4b4]">{floorName(d.floor)}</span>
            </div>
            <p className="text-xl text-[#b4b4b4]">
              {d.department} · {d.doctor_name}
            </p>
            {d.status !== "in" && (
              <p className="mt-2 inline-block rounded bg-[#204f65] px-2 py-1 text-lg">
                {d.status === "break" ? "Doctor on a short break" : d.status === "away" ? "Doctor away" : "Doctor running late"}
              </p>
            )}
            <div className={`mt-4 rounded-2xl px-5 py-4 ${isCalling(d.doctor_id) ? "board-call" : "bg-[#164055]"}`}>
              <p className="text-sm uppercase tracking-widest opacity-80">Now</p>
              <AnimatePresence mode="popLayout" initial={false}>
                <motion.p
                  key={d.serving ? `${d.serving.token}-${d.serving.call_count}` : "none"}
                  className={`text-7xl font-black tracking-[-0.04em] tabular-nums ${isCalling(d.doctor_id) ? "" : "text-[#9fe5cf]"}`}
                  initial={{ rotateX: -90, opacity: 0, y: 30 }}
                  animate={{ rotateX: 0, opacity: 1, y: 0 }}
                  exit={{ rotateX: 90, opacity: 0, y: -30 }}
                  transition={{ type: "spring", stiffness: 200, damping: 20 }}
                  style={{ transformPerspective: 600 }}
                >
                  {d.serving?.token ?? "—"}
                </motion.p>
              </AnimatePresence>
              <p className="text-2xl">{d.serving?.first_name ?? ""}</p>
            </div>
            <p className="mt-3 text-2xl text-[#d6d6d6]">
              <span className="text-base uppercase tracking-widest text-[#9a9a9a]">Next </span>
              {d.next.length ? d.next.join("   ") : "—"}
            </p>
          </section>
        ))}
      </main>

      <footer className="fixed inset-x-0 bottom-0 bg-[#a3161c] px-6 py-3 text-center text-2xl font-bold">
        Feeling worse? Tell any staff member now. Emergency 112 · Ambulance 108
      </footer>
    </div>
    </MotionConfig>
  );
}
