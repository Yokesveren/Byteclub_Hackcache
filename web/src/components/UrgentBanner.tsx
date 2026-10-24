"use client";

import { AnimatePresence, motion } from "motion/react";
import { useEffect, useRef, useState } from "react";
import { floorName, hhmm, secondsSince } from "@/lib/format";
import { useStoredValue } from "@/lib/hooks";
import { rpc } from "@/lib/supabase";
import type { UrgentFlag } from "@/lib/types";

const SOUND_KEY = "sevasetu.sound";

/** Two-tone alarm via Web Audio, so no audio file is needed offline. */
function beep(ctx: AudioContext) {
  const t = ctx.currentTime;
  [880, 660].forEach((freq, i) => {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = "square";
    osc.frequency.value = freq;
    gain.gain.setValueAtTime(0.0001, t + i * 0.22);
    gain.gain.exponentialRampToValueAtTime(0.25, t + i * 0.22 + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, t + i * 0.22 + 0.2);
    osc.connect(gain).connect(ctx.destination);
    osc.start(t + i * 0.22);
    osc.stop(t + i * 0.22 + 0.21);
  });
}

/**
 * Urgent flags sit above everything with sound and a flashing border until a
 * staff member taps Seen (S5). Time to acknowledge is logged by the server.
 */
export function UrgentBanner({ flags, now, onSeen }: { flags: UrgentFlag[]; now: number; onSeen: () => void }) {
  const [soundPref, setSoundPref] = useStoredValue(SOUND_KEY, "off");
  const soundOn = soundPref === "on";
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const ctxRef = useRef<AudioContext | null>(null);

  const enableSound = () => {
    ctxRef.current ??= new AudioContext();
    void ctxRef.current.resume();
    setSoundPref("on");
  };

  const open = flags.length > 0;
  useEffect(() => {
    if (!open || !soundOn) return;
    ctxRef.current ??= new AudioContext();
    const ctx = ctxRef.current;
    const play = () => {
      if (ctx.state === "suspended") void ctx.resume();
      beep(ctx);
    };
    play();
    const id = setInterval(play, 1600);
    return () => clearInterval(id);
  }, [open, soundOn]);

  const seen = async (id: string) => {
    setBusy(id);
    setError(null);
    try {
      await rpc("ack_urgent_flag", { p_id: id });
      onSeen();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(null);
    }
  };

  // Browsers block audio until the page is touched once; ask for that up front.
  const soundPrompt = !soundOn && (
    <button
      onClick={enableSound}
      className="rounded-full bg-[#9fe5cf] px-4 py-1.5 text-sm font-bold text-[#0b2a3c] hover:bg-[#b8ecdc]"
    >
      Turn on alarm sound
    </button>
  );

  if (!open) {
    return soundPrompt ? (
      <div className="flex flex-wrap items-center gap-3 bg-[#0b2a3c] px-4 py-2.5 text-sm text-[#d6d6d6] sm:px-8">
        Urgent-help alarm sound is off on this screen. {soundPrompt}
      </div>
    ) : null;
  }

  return (
    <section
      role="alert"
      aria-live="assertive"
      className="urgent-flash sticky top-12 z-20 lg:top-0 border-y-4 border-red-700 bg-red-50 px-4 py-3"
    >
      <div className="mb-2 flex flex-wrap items-center gap-3">
        <h2 className="text-lg font-extrabold uppercase tracking-wide text-red-800">
          Urgent help requested ({flags.length})
        </h2>
        <span className="text-sm text-red-900">Go to the patient now. Tap Seen once a staff member is on the way.</span>
        {soundPrompt}
      </div>
      {error && <p className="mb-2 text-sm font-semibold text-red-800">{error}</p>}
      <ul className="grid gap-2 md:grid-cols-2 xl:grid-cols-3">
        <AnimatePresence initial={true}>
        {flags.map((f) => (
          <motion.li
            key={f.id}
            layout
            initial={{ opacity: 0, scale: 0.85, y: -20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.9, x: 80 }}
            transition={{ type: "spring", stiffness: 420, damping: 22 }}
            className="flex items-start gap-3 rounded-lg border-2 border-red-700 bg-white p-3"
          >
            <div className="flex-1">
              <p className="text-xl font-bold text-red-800">
                {f.token ? `${f.token} · ${f.first_name}` : "Patient without a token"}
              </p>
              <p className="text-sm text-[#1a2228]">
                {f.room ? `${f.doctor_name}, ${f.room}, ${floorName(f.floor ?? 0)}` : "Location: "}
                {f.waypoint_id && ` · last scan ${f.waypoint_id}`}
                {!f.room && !f.waypoint_id && "unknown, check waiting areas"}
              </p>
              {f.message && <p className="mt-1 text-sm italic text-[#2b363d]">“{f.message}”</p>}
              <p className="mt-1 text-xs text-[var(--muted)]">
                {f.source.replace("_", " ")} · received {hhmm(f.received_at)} ·{" "}
                <span className="font-semibold text-red-800">{secondsSince(f.received_at, now)} s ago</span>
                {f.needs_stair_free && " · needs a stair-free route"}
              </p>
            </div>
            <motion.button
              whileTap={{ scale: 0.92 }}
              onClick={() => seen(f.id)}
              disabled={busy === f.id}
              className="min-h-12 rounded-lg bg-red-700 px-5 text-lg font-bold text-white hover:bg-red-800 disabled:opacity-60"
            >
              {busy === f.id ? "…" : "Seen"}
            </motion.button>
          </motion.li>
        ))}
        </AnimatePresence>
      </ul>
    </section>
  );
}
