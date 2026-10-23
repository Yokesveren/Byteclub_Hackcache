"use client";

import { AnimatePresence, motion, MotionConfig } from "motion/react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { createContext, useContext, useEffect, useRef, useState } from "react";
import { hhmmss } from "@/lib/format";
import { useAppClock, useHospital, useStaff } from "@/lib/hooks";
import { canOpen, ROLE_HOME, STAFF_ROUTES } from "@/lib/routes";
import { supabase } from "@/lib/supabase";
import type { Staff } from "@/lib/types";

// Shell in the landing page's language: a night field holds the sidebar (black capitals, a mustard
// pill that slides to the active page); the work sits on a white sheet inset 8px, 20px corners.

const NAV_ICON: Record<string, keyof typeof ICONS> = { "/facility": "queue", "/counter": "slip", "/flow": "map", "/log": "log", "/demo": "clock" };
const NAV = STAFF_ROUTES.map((r) => ({ ...r, icon: NAV_ICON[r.href] }));

const HOSPITALS = [
  { id: "H01", name: "Chinar District Hospital" },
  { id: "H02", name: "Jhelum Sub-District Hospital" },
  { id: "H03", name: "Lidder Community Health Centre" },
];

const ICONS = {
  queue: "M4 6h16M4 12h10M4 18h6",
  slip: "M7 3h10v18l-2.5-1.5L12 21l-2.5-1.5L7 21zM10 8h4M10 12h4",
  log: "M5 4h14v16H5zM9 9h6M9 13h6M9 17h3",
  clock: "M12 7v5l3 2M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18",
  board: "M3 5h18v12H3zM8 21h8M12 17v4",
  map: "M9 4L3 6v14l6-2 6 2 6-2V4l-6 2-6-2zM9 4v14M15 6v14",
  out: "M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10",
};

function Icon({ d }: { d: string }) {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d={d} />
    </svg>
  );
}

/** Setu is a bridge: one arch over the water line. */
export function Mark({ size = 24 }: { size?: number }) {
  return (
    <svg viewBox="0 0 30 30" width={size} height={size} aria-hidden>
      <rect width="30" height="30" rx="6" fill="#b0298f" />
      <path d="M5 21 Q15 5 25 21" fill="none" stroke="#ffffff" strokeWidth="3.4" />
      <rect x="4" y="21" width="22" height="3.4" fill="#ffffff" />
    </svg>
  );
}

/** Never a blank screen: a branded wait, and after 8 seconds a way out. */
function ShellWaiting({ redirecting, message }: { redirecting: boolean; message?: string }) {
  const router = useRouter();
  const [stuck, setStuck] = useState(false);
  useEffect(() => {
    const id = setTimeout(() => setStuck(true), 8000);
    return () => clearTimeout(id);
  }, []);

  return (
    <div className="app-frame grid min-h-screen place-items-center p-6 text-white">
      <div className="flex flex-col items-center text-center">
        <motion.div animate={{ scale: [1, 1.12, 1] }} transition={{ duration: 1.2, repeat: Infinity, ease: "easeInOut" }}>
          <Mark size={56} />
        </motion.div>
        <p className="mt-6 text-3xl font-black uppercase tracking-[-0.04em]">
          {message ?? (redirecting ? "Taking you to sign in" : "Opening the dashboard")}
        </p>
        {stuck && !redirecting && (
          <motion.div className="mt-6 max-w-sm" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
            <p className="text-[#c9c9c9]">This is taking longer than it should. Close any other SevaSetu tabs, then try again.</p>
            <div className="mt-5 flex justify-center gap-3">
              <button onClick={() => window.location.reload()} className="h-11 rounded-xl bg-[#ffc013] px-5 font-bold text-[#0a0a0a]">
                Reload
              </button>
              <button
                onClick={async () => {
                  await supabase.auth.signOut().catch(() => undefined);
                  router.replace("/login");
                }}
                className="h-11 rounded-xl bg-[#262626] px-5 font-bold text-white"
              >
                Sign in again
              </button>
            </div>
          </motion.div>
        )}
      </div>
    </div>
  );
}

export interface ShellContext {
  staff: Staff;
  hospitalId: string;
  now: number;
  speed: number;
}

const ShellCtx = createContext<ShellContext | null>(null);

/** The signed-in staff member, the hospital in view and the demo clock, for any staff page. */
export function useShell(): ShellContext {
  const ctx = useContext(ShellCtx);
  if (!ctx) throw new Error("useShell must be used inside the staff layout");
  return ctx;
}

/**
 * Mounted once by app/(staff)/layout.tsx, so it survives navigation: no re-check of the session
 * and no loading flash between staff pages, and the sidebar pill slides from page to page.
 */
export function StaffShell({ children }: { children: React.ReactNode }) {
  const staff = useStaff();
  const [hospitalId, setHospitalId] = useHospital(staff);
  const { now, speed } = useAppClock();
  const router = useRouter();
  const pathname = usePathname();
  const [menu, setMenu] = useState(false);
  const signingOut = useRef(false);
  const allowed = staff ? canOpen(staff.role, pathname) : true;

  // Signed out (or the session expired): to sign-in, coming back here afterwards unless it was a deliberate sign-out.
  useEffect(() => {
    if (staff !== null) return;
    router.replace(signingOut.current ? "/login" : `/login?next=${encodeURIComponent(pathname)}`);
  }, [staff, router, pathname]);

  // A page this role may not open: to the role's own home.
  useEffect(() => {
    if (staff && !allowed) router.replace(ROLE_HOME[staff.role]);
  }, [staff, allowed, router]);

  const signOut = async () => {
    signingOut.current = true;
    await supabase.auth.signOut();
  };

  if (!staff) {
    return <ShellWaiting redirecting={staff === null} />;
  }
  if (!allowed) {
    return <ShellWaiting redirecting={false} message="Taking you to your dashboard" />;
  }

  const hospitalName = HOSPITALS.find((h) => h.id === hospitalId)?.name ?? hospitalId;
  const items = NAV.filter((n) => n.roles.includes(staff.role));

  const nav = (
    <>
      <div className="px-4">
        <p className="text-xs text-[#9a9a9a]">Hospital</p>
        {staff.hospital_id ? (
          <p className="mt-1 text-sm font-bold leading-tight text-white">{hospitalName}</p>
        ) : (
          <select
            value={hospitalId}
            onChange={(e) => setHospitalId(e.target.value)}
            className="mt-1.5 h-10 w-full rounded-lg border-0 bg-[#1c1c1c] px-2.5 text-sm font-bold text-white outline-none ring-1 ring-[#2e2e2e] focus:ring-2 focus:ring-[#ffc013]"
            aria-label="Hospital"
          >
            {HOSPITALS.map((h) => (
              <option key={h.id} value={h.id}>
                {h.name}
              </option>
            ))}
          </select>
        )}
      </div>
      <nav className="mt-8 flex flex-col gap-1 px-2" aria-label="Dashboard">
        {items.map((n) => {
          const active = pathname.startsWith(n.href);
          return (
            <Link
              key={n.href}
              href={n.href}
              onClick={() => setMenu(false)}
              aria-current={active ? "page" : undefined}
              className={`relative flex h-11 items-center gap-3 rounded-xl px-3 text-[15px] font-bold transition-colors ${
                active ? "text-[#0a0a0a]" : "text-[#c9c9c9] hover:bg-[#1a1a1a] hover:text-white"
              }`}
            >
              {active && (
                <motion.span
                  layoutId="nav-pill"
                  className="absolute inset-0 rounded-xl bg-[#ffc013]"
                  transition={{ type: "spring", stiffness: 420, damping: 34 }}
                />
              )}
              <span className="relative z-10 flex items-center gap-3">
                <Icon d={ICONS[n.icon]} />
                {n.label}
              </span>
            </Link>
          );
        })}
        <a
          href={`/board/${hospitalId}`}
          target="_blank"
          rel="noreferrer"
          className="flex h-11 items-center gap-3 rounded-xl px-3 text-[15px] font-bold text-[#c9c9c9] transition-colors hover:bg-[#1a1a1a] hover:text-white"
        >
          <Icon d={ICONS.board} />
          Public board
          <span className="ml-auto text-xs font-normal text-[#8a8a8a]">new tab ↗</span>
        </a>
      </nav>
      <div className="mt-auto px-4 pb-5">
        <p className="text-xs text-[#9a9a9a]">Hospital time</p>
        <p className="mt-1 flex flex-wrap items-baseline gap-2 text-[28px] font-black leading-none tracking-[-0.03em] text-white tabular-nums" title="Demo clock">
          {now ? hhmmss(now) : "--:--:--"}
          {speed !== 1 && <span className="rounded-md bg-[#ffc013] px-1.5 py-0.5 text-xs font-bold tracking-normal text-[#0a0a0a]">{speed}× speed</span>}
        </p>
        <div className="mt-5 flex items-center gap-3 rounded-xl bg-[#161616] p-2.5">
          <span className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-[#b0298f] text-sm font-black text-white" aria-hidden>
            {staff.name.replace(/^(Dr\.|Nurse)\s+/, "").charAt(0)}
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-bold text-white">{staff.name}</span>
            <span className="block text-xs capitalize text-[#9a9a9a]">{staff.role === "demo" ? "Demo operator" : staff.role}</span>
          </span>
          <motion.button
            whileTap={{ scale: 0.9 }}
            onClick={signOut}
            className="grid h-9 w-9 place-items-center rounded-lg text-[#c9c9c9] hover:bg-[#262626] hover:text-white"
            aria-label="Sign out"
            title="Sign out"
          >
            <Icon d={ICONS.out} />
          </motion.button>
        </div>
      </div>
    </>
  );

  return (
    <MotionConfig reducedMotion="user">
      <div className="app-frame min-h-screen lg:grid lg:grid-cols-[248px_minmax(0,1fr)]">
        {/* Desktop sidebar: the night field, like the landing page's dark chapters */}
        <aside className="sticky top-0 hidden h-screen flex-col pt-5 lg:flex">
          <Link href="/" className="mb-8 flex items-center gap-3 px-4">
            <Mark size={30} />
            <span className="text-xl font-black uppercase tracking-[-0.04em] text-white">SevaSetu</span>
          </Link>
          {nav}
        </aside>

        {/* Phone: the frame folds into a bar with a menu sheet */}
        <header className="sticky top-0 z-40 flex h-14 items-center gap-3 px-3 text-white lg:hidden">
          <button
            onClick={() => setMenu((m) => !m)}
            aria-expanded={menu}
            aria-label="Menu"
            className="grid h-10 w-10 place-items-center rounded-lg hover:bg-[#1a1a1a]"
          >
            <Icon d={menu ? "M6 6l12 12M18 6L6 18" : "M4 7h16M4 12h16M4 17h16"} />
          </button>
          <Mark size={24} />
          <span className="truncate text-sm font-black uppercase tracking-tight">{hospitalName}</span>
          <span className="ml-auto text-sm font-bold tabular-nums">{now ? hhmmss(now).slice(0, 5) : "--:--"}</span>
        </header>
        <AnimatePresence>
          {menu && (
            <motion.div
              className="app-frame fixed inset-x-0 bottom-0 top-14 z-40 flex flex-col overflow-y-auto pt-4 lg:hidden"
              initial={{ opacity: 0, y: -16 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -16 }}
              transition={{ type: "spring", stiffness: 400, damping: 34 }}
            >
              {nav}
            </motion.div>
          )}
        </AnimatePresence>

        <main className="app-sheet min-w-0">
          <ShellCtx.Provider value={{ staff, hospitalId, now, speed }}>
            <motion.div
              key={pathname}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ type: "spring", stiffness: 260, damping: 30 }}
            >
              {children}
            </motion.div>
          </ShellCtx.Provider>
        </main>
      </div>
    </MotionConfig>
  );
}
