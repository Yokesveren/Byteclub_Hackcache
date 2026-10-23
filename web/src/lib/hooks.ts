"use client";

import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import type { RealtimeChannel } from "@supabase/supabase-js";
import { DEFAULT_HOSPITAL, rpc, supabase } from "./supabase";
import type { Staff } from "./types";

/** Current staff member, or null when signed out. undefined while loading. */
export function useStaff(): Staff | null | undefined {
  const [staff, setStaff] = useState<Staff | null | undefined>(undefined);

  useEffect(() => {
    let alive = true;
    const loadFor = async (userId: string | null) => {
      if (!userId) {
        if (alive) setStaff(null);
        return;
      }
      const { data } = await supabase.from("staff").select("*").eq("user_id", userId).maybeSingle();
      if (alive) setStaff((data as Staff) ?? null);
    };
    // onAuthStateChange fires INITIAL_SESSION on subscribe, so no separate getSession() is needed.
    // Never await Supabase calls inside this callback: it runs while the auth lock is held and
    // can deadlock (the staff screen then hangs on "Loading"). Defer the lookup instead.
    const { data: sub } = supabase.auth.onAuthStateChange((_event, session) => {
      const userId = session?.user.id ?? null;
      setTimeout(() => void loadFor(userId), 0);
    });
    return () => {
      alive = false;
      sub.subscription.unsubscribe();
    };
  }, []);

  return staff;
}

const STORAGE_EVENT = "sevasetu-storage";

function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null; // private mode or blocked storage
  }
}

/** A per-browser preference in localStorage. Falls back to `fallback` when storage is unavailable. */
export function useStoredValue(key: string, fallback: string): [string, (v: string) => void] {
  const value = useSyncExternalStore(
    (onChange) => {
      window.addEventListener("storage", onChange);
      window.addEventListener(STORAGE_EVENT, onChange);
      return () => {
        window.removeEventListener("storage", onChange);
        window.removeEventListener(STORAGE_EVENT, onChange);
      };
    },
    () => readStorage(key) ?? fallback,
    () => fallback,
  );

  const set = useCallback(
    (v: string) => {
      try {
        localStorage.setItem(key, v);
      } catch {
        /* ignore */
      }
      window.dispatchEvent(new Event(STORAGE_EVENT));
    },
    [key],
  );

  return [value, set];
}

/** Hospital the dashboard is looking at. Facility staff are pinned to their own. */
export function useHospital(staff: Staff | null | undefined): [string, (id: string) => void] {
  const [chosen, setChosen] = useStoredValue("sevasetu.hospital", DEFAULT_HOSPITAL);
  return [staff?.hospital_id ?? chosen, setChosen];
}

interface Clock {
  now: string;
  speed: number;
}

/** Demo-aware clock: follows the server's app_now(), including speed-ups. */
export function useAppClock(tickMs = 1000): { now: number; speed: number } {
  const base = useRef<{ sim: number; real: number; speed: number } | null>(null);
  const [state, setState] = useState<{ now: number; speed: number }>({ now: 0, speed: 1 });

  useEffect(() => {
    let alive = true;
    const sync = async () => {
      try {
        const c = await rpc<Clock>("get_clock");
        if (alive) base.current = { sim: new Date(c.now).getTime(), real: Date.now(), speed: c.speed };
      } catch {
        /* offline: keep extrapolating */
      }
    };
    sync();
    const syncTimer = setInterval(sync, 10000);
    const tick = setInterval(() => {
      const b = base.current ?? { sim: Date.now(), real: Date.now(), speed: 1 };
      setState({ now: b.sim + (Date.now() - b.real) * b.speed, speed: b.speed });
    }, tickMs);
    return () => {
      alive = false;
      clearInterval(syncTimer);
      clearInterval(tick);
    };
  }, [tickMs]);

  return state;
}

/** Wall-clock time that re-renders every `tickMs`; for UI timers, not hospital time. */
export function useRealNow(tickMs = 1000): number {
  const [now, setNow] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), tickMs);
    return () => clearInterval(id);
  }, [tickMs]);
  return now;
}

/**
 * Loads data with `load` and reloads it (debounced) whenever the hospital's
 * realtime events fire. Also polls as a safety net.
 */
export function useLiveData<T>(
  load: () => Promise<T>,
  opts: { hospitalId: string | null; tables?: string[]; pollMs?: number; enabled?: boolean },
) {
  const { hospitalId, tables = [], pollMs = 15000, enabled = true } = opts;
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [connected, setConnected] = useState(false);
  const [updatedAt, setUpdatedAt] = useState<number | null>(null);
  const loadRef = useRef(load);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    loadRef.current = load;
  }, [load]);

  const refresh = useCallback(async () => {
    try {
      const d = await loadRef.current();
      setData(d);
      setError(null);
      setUpdatedAt(Date.now());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  const schedule = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(refresh, 150);
  }, [refresh]);

  const tablesKey = tables.join(",");

  useEffect(() => {
    if (!enabled || !hospitalId) return;
    const first = setTimeout(refresh, 0);
    const channels: RealtimeChannel[] = [];

    // Public broadcast from database triggers (works for every viewer).
    const bc = supabase
      .channel(`hospital:${hospitalId}`)
      .on("broadcast", { event: "queue_changed" }, schedule)
      .subscribe((status) => setConnected(status === "SUBSCRIBED"));
    channels.push(bc);

    // Staff-only table changes (urgent flags have no public broadcast).
    if (tablesKey) {
      const pc = supabase.channel(`staff:${hospitalId}:${tablesKey}`);
      for (const table of tablesKey.split(",")) {
        pc.on(
          "postgres_changes",
          { event: "*", schema: "public", table, filter: `hospital_id=eq.${hospitalId}` },
          schedule,
        );
      }
      pc.subscribe();
      channels.push(pc);
    }

    const poll = setInterval(refresh, pollMs);
    return () => {
      clearTimeout(first);
      clearInterval(poll);
      if (timer.current) clearTimeout(timer.current);
      channels.forEach((c) => supabase.removeChannel(c));
    };
  }, [enabled, hospitalId, tablesKey, pollMs, refresh, schedule]);

  return { data, error, connected, updatedAt, refresh };
}
