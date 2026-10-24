"use client";

import { AnimatePresence, motion, MotionConfig } from "motion/react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Mark } from "@/components/StaffShell";
import { safeNext } from "@/lib/routes";
import { supabase } from "@/lib/supabase";
import type { StaffRole } from "@/lib/types";

// Local demo accounts from supabase/seed.sql. Never ship these to a real deployment.
// Each role gets one of the landing page's chapter colours.
const DEMO_ACCOUNTS: { email: string; label: string; does: string; bg: string; ink: string }[] = [
  { email: "triage@sevasetu.demo", label: "Triage nurse", does: "Queues, urgent flags, bands", bg: "#9fe5cf", ink: "#0b2a3c" },
  { email: "counter@sevasetu.demo", label: "Counter", does: "Register, print the slip", bg: "#ffffff", ink: "#0b2a3c" },
  { email: "doctor@sevasetu.demo", label: "Doctor", does: "Own queue, Room 3", bg: "#edf3f8", ink: "#0b2a3c" },
  { email: "admin@sevasetu.demo", label: "District officer", does: "All three hospitals", bg: "#0b2a3c", ink: "#f2f2f2" },
  { email: "demo@sevasetu.demo", label: "Demo operator", does: "Clock, autopilot, reset", bg: "#0b7a75", ink: "#ffffff" },
];

// The hospital as a graph of QR waypoints, drawing itself behind the headline.
const NODES: [number, number][] = [
  [60, 80], [260, 40], [480, 120], [120, 300], [380, 280], [560, 360], [80, 520], [300, 600], [520, 560],
];
const EDGES: [number, number][] = [[0, 1], [1, 2], [0, 3], [3, 4], [2, 4], [4, 5], [3, 6], [6, 7], [7, 8], [5, 8], [4, 7]];
const ROUTE = "M80 520 L300 600 L380 280 L560 360 L520 560";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  // Already signed in: skip the form and go straight to where this person was heading.
  useEffect(() => {
    let alive = true;
    (async () => {
      const { data } = await supabase.auth.getSession();
      if (!data.session || !alive) return;
      const { data: staff } = await supabase.from("staff").select("role").eq("user_id", data.session.user.id).maybeSingle();
      if (staff && alive) {
        router.replace(safeNext(new URLSearchParams(window.location.search).get("next"), staff.role as StaffRole));
      }
    })();
    return () => {
      alive = false;
    };
  }, [router]);

  const signIn = async (e?: React.FormEvent, override?: { email: string; password: string }) => {
    e?.preventDefault();
    const creds = override ?? { email, password };
    setBusy(creds.email || "form");
    setError(null);
    const { data, error } = await supabase.auth.signInWithPassword(creds);
    if (error || !data.user) {
      setError(error?.message ?? "Sign-in failed");
      setBusy(null);
      return;
    }
    const { data: staff } = await supabase.from("staff").select("role").eq("user_id", data.user.id).maybeSingle();
    if (!staff) {
      setError("This account is not registered as hospital staff.");
      await supabase.auth.signOut();
      setBusy(null);
      return;
    }
    const next = new URLSearchParams(window.location.search).get("next");
    router.replace(safeNext(next, staff.role as StaffRole));
  };

  return (
    <MotionConfig reducedMotion="user">
      <div className="grid min-h-screen bg-[#0b2a3c] lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
        {/* Left: the night chapter */}
        <section className="relative flex min-h-[46vh] flex-col justify-between overflow-hidden p-6 text-[#f2f2f2] sm:p-10">
          <svg className="pointer-events-none absolute inset-0 h-full w-full opacity-90" viewBox="0 0 620 680" preserveAspectRatio="xMidYMid slice" aria-hidden>
            {EDGES.map(([a, b], i) => (
              <motion.line
                key={i}
                x1={NODES[a][0]}
                y1={NODES[a][1]}
                x2={NODES[b][0]}
                y2={NODES[b][1]}
                stroke="#204f65"
                strokeWidth="2"
                initial={{ pathLength: 0 }}
                animate={{ pathLength: 1 }}
                transition={{ duration: 1, delay: 0.2 + i * 0.06 }}
              />
            ))}
            <motion.path
              d={ROUTE}
              fill="none"
              stroke="#5fd3c6"
              strokeWidth="5"
              strokeLinecap="round"
              strokeLinejoin="round"
              initial={{ pathLength: 0 }}
              animate={{ pathLength: [0, 1, 1], opacity: [1, 1, 0] }}
              transition={{ duration: 3.6, delay: 1, times: [0, 0.75, 1], repeat: Infinity, repeatDelay: 0.6 }}
            />
            {NODES.map(([x, y], i) => (
              <motion.rect
                key={i}
                x={x - 7}
                y={y - 7}
                width="14"
                height="14"
                fill="#9fe5cf"
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                transition={{ type: "spring", stiffness: 300, damping: 14, delay: 0.1 + i * 0.05 }}
                style={{ transformOrigin: `${x}px ${y}px` }}
              />
            ))}
          </svg>

          <Link href="/" className="relative z-10 flex items-center gap-3">
            <Mark size={34} />
            <span className="text-2xl font-black uppercase tracking-[-0.04em]">SevaSetu</span>
          </Link>

          <div className="relative z-10 mt-16">
            <h1 className="text-[clamp(3.5rem,2rem+6vw,7.5rem)] font-black uppercase leading-[0.86] tracking-[-0.05em]">
              {["Staff", "sign-in"].map((w, i) => (
                <span key={w} className="block overflow-hidden pb-[0.04em]">
                  <motion.span
                    className="block"
                    initial={{ y: "105%" }}
                    animate={{ y: "0%" }}
                    transition={{ type: "spring", stiffness: 160, damping: 18, delay: 0.1 + i * 0.12 }}
                  >
                    {w}
                  </motion.span>
                </span>
              ))}
            </h1>
            <motion.p
              className="mt-6 max-w-[34ch] text-lg leading-snug text-[#d6d6d6]"
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.5 }}
            >
              The hospital dashboard: live queues, urgent flags and the registration counter for Chinar, Jhelum and Lidder
              (demo hospitals).
            </motion.p>
          </div>

          <p className="relative z-10 mt-10 text-sm text-[#9a9a9a]">
            Emergency? Call <a href="tel:112" className="font-bold text-white underline">112</a> · Ambulance{" "}
            <a href="tel:108" className="font-bold text-white underline">108</a>
          </p>
        </section>

        {/* Right: the sky chapter with the form */}
        <section className="flex items-center bg-[#dcecf7] p-4 sm:p-10 lg:rounded-l-[32px]">
          <motion.div
            className="mx-auto w-full max-w-[560px]"
            initial={{ opacity: 0, y: 40 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ type: "spring", stiffness: 120, damping: 18, delay: 0.2 }}
          >
            <form onSubmit={signIn} className="rounded-[24px] bg-white p-6 sm:p-8">
              <h2 className="text-2xl font-black uppercase tracking-[-0.03em]">Your account</h2>
              <label className="mt-6 block text-sm font-bold">
                Email
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  autoComplete="username"
                  required
                  className="mt-1.5 h-12 w-full rounded-xl bg-[#f1f4f6] px-4 text-base font-normal outline-none ring-[#0b2a3c] transition focus:bg-white focus:ring-2"
                />
              </label>
              <label className="mt-4 block text-sm font-bold">
                Password
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  required
                  className="mt-1.5 h-12 w-full rounded-xl bg-[#f1f4f6] px-4 text-base font-normal outline-none ring-[#0b2a3c] transition focus:bg-white focus:ring-2"
                />
              </label>
              <AnimatePresence>
                {error && (
                  <motion.p
                    className="mt-4 rounded-xl bg-[#c4242a] px-4 py-3 text-sm font-bold text-white"
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: "auto" }}
                    exit={{ opacity: 0, height: 0 }}
                  >
                    {error}
                  </motion.p>
                )}
              </AnimatePresence>
              <motion.button
                whileTap={{ scale: 0.97 }}
                disabled={busy !== null}
                className="mt-6 h-14 w-full rounded-xl bg-[#0b7a75] text-lg font-black uppercase tracking-[-0.01em] text-white transition-colors hover:bg-[#08615d] disabled:opacity-60"
              >
                {busy === "form" ? "Signing in…" : "Sign in"}
              </motion.button>
            </form>

            {process.env.NODE_ENV !== "production" && (
              <div className="mt-6">
                <p className="text-sm font-bold">Demo accounts, local only: one tap signs in</p>
                <motion.div
                  className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3"
                  initial="hidden"
                  animate="show"
                  variants={{ hidden: {}, show: { transition: { staggerChildren: 0.06, delayChildren: 0.4 } } }}
                >
                  {DEMO_ACCOUNTS.map((a) => (
                    <motion.button
                      key={a.email}
                      type="button"
                      disabled={busy !== null}
                      onClick={() => signIn(undefined, { email: a.email, password: "sevasetu-demo" })}
                      variants={{ hidden: { opacity: 0, y: 20, scale: 0.95 }, show: { opacity: 1, y: 0, scale: 1 } }}
                      whileHover={{ y: -4 }}
                      whileTap={{ scale: 0.96 }}
                      transition={{ type: "spring", stiffness: 300, damping: 20 }}
                      className="flex min-h-[104px] flex-col justify-between rounded-2xl p-4 text-left disabled:opacity-60"
                      style={{ background: a.bg, color: a.ink }}
                    >
                      <span className="text-lg font-black uppercase leading-[0.95] tracking-[-0.03em]">
                        {busy === a.email ? "Signing in…" : a.label}
                      </span>
                      <span className="mt-2 text-xs font-bold opacity-80">{a.does}</span>
                    </motion.button>
                  ))}
                </motion.div>
              </div>
            )}
          </motion.div>
        </section>
      </div>
    </MotionConfig>
  );
}
