"use client";

import { AnimatePresence, motion } from "motion/react";
import { useCallback, useEffect, useState } from "react";
import { supabase } from "@/lib/supabase";
import type { Board } from "@/lib/types";
import { Rise, Stagger } from "./fx";

const HOSPITAL = "H01";

/** The real waiting-room board of the demo hospital, live. Token and first name only (S9). */
export function LiveBoardFx() {
  const [board, setBoard] = useState<Board | null>(null);
  const [offline, setOffline] = useState(false);

  const load = useCallback(async () => {
    const { data, error } = await supabase.rpc("get_board", { p_hospital_id: HOSPITAL });
    if (error || !data) {
      setOffline(true);
      return;
    }
    setOffline(false);
    setBoard(data as Board);
  }, []);

  useEffect(() => {
    const first = setTimeout(load, 0);
    const channel = supabase
      .channel(`landing:${HOSPITAL}`)
      .on("broadcast", { event: "queue_changed" }, () => void load())
      .subscribe();
    const poll = setInterval(load, 8000);
    return () => {
      clearTimeout(first);
      clearInterval(poll);
      supabase.removeChannel(channel);
    };
  }, [load]);

  const doctors = (board?.doctors ?? []).filter((d) => d.serving).slice(0, 6);

  return (
    <section className="ss-live" aria-labelledby="live-title">
      <Stagger className="ss-live-head">
        <Rise as="h2">
          <span id="live-title" className="ss-display">
            Live from Room 3
          </span>
        </Rise>
        <Rise as="p" className="ss-live-sub">
          <span className="ss-live-dot" aria-hidden>
            <motion.span animate={{ scale: [1, 2.4], opacity: [0.6, 0] }} transition={{ duration: 1.6, repeat: Infinity }} />
          </span>
          {offline
            ? "The demo hospital is offline in this preview. Start the backend to see it move."
            : "The real waiting-room board of the demo hospital, right now. Tokens flip as doctors call the next patient."}
        </Rise>
      </Stagger>

      <Stagger className="ss-live-grid">
        {doctors.map((d) => (
          <Rise key={d.doctor_id} className="ss-live-card">
            <div className="ss-live-room">
              <strong>{d.room}</strong>
            </div>
            <div className="ss-live-token" aria-live="polite">
              <AnimatePresence mode="popLayout" initial={false}>
                <motion.span
                  key={`${d.serving!.token}-${d.serving!.call_count}`}
                  initial={{ rotateX: -90, opacity: 0, y: 30 }}
                  animate={{ rotateX: 0, opacity: 1, y: 0 }}
                  exit={{ rotateX: 90, opacity: 0, y: -30 }}
                  transition={{ type: "spring", stiffness: 200, damping: 20 }}
                  style={{ transformPerspective: 600, display: "block" }}
                >
                  {d.serving!.token}
                </motion.span>
              </AnimatePresence>
            </div>
            <p className="ss-live-name">{d.serving!.first_name}</p>
            <p className="ss-live-next">
              {d.department} · next {d.next.length ? d.next.join("  ") : "none waiting"}
            </p>
          </Rise>
        ))}
        {!board &&
          !offline &&
          Array.from({ length: 6 }, (_, i) => (
            <Rise key={i} className="ss-live-card ss-live-skeleton">
              <div className="ss-live-room">
                <strong>Room</strong>
                <span>Loading</span>
              </div>
              <div className="ss-live-token">—</div>
            </Rise>
          ))}
      </Stagger>
    </section>
  );
}
