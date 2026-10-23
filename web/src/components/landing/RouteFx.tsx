"use client";

import { AnimatePresence, motion, useMotionValueEvent, useScroll, useSpring, useTransform } from "motion/react";
import { useEffect, useRef, useState } from "react";

// Ground and first floor of the demo hospital, flattened into one plan. Waypoint ids match the seed data.
const WAYPOINTS: { id: string; x: number; y: number; label: string }[] = [
  { id: "W001", x: 70, y: 470, label: "Gate" },
  { id: "W004", x: 70, y: 300, label: "Registration" },
  { id: "W009", x: 330, y: 300, label: "Pharmacy corner" },
  { id: "W012", x: 520, y: 300, label: "Stairs" },
  { id: "W015", x: 330, y: 150, label: "Lift" },
  { id: "W103", x: 700, y: 150, label: "Room 3" },
];
const ROUTE = "M70 470 L70 300 L330 300 L330 150 L700 150";
const WRONG = "M330 300 L520 300";

const STEPS = [
  { at: 0, title: "Scan at the gate", text: "Sticker W001. The phone now knows exactly where Kamla is: no GPS, no beacons." },
  { at: 0.28, title: "A wrong turn", text: "She walks on towards the stairs and scans W012. The remaining distance went up, so it counts." },
  { at: 0.5, title: "No problem. New route.", text: "Dijkstra re-plans from W012 with stairs removed from the graph: back to the lift." },
  { at: 0.74, title: "You have reached Room 3", text: "Scanning W103 confirms arrival. Entry to room: 4 minutes, one wrong turn, logged without blame." },
];

export function RouteFx() {
  const ref = useRef<HTMLElement>(null);
  const pathRef = useRef<SVGPathElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start start", "end end"] });
  const p = useSpring(scrollYProgress, { stiffness: 90, damping: 24, mass: 0.4 });

  // Route length: the wrong turn happens in the middle, so the main route pauses while the detour shows.
  const routeLen = useTransform(p, [0.05, 0.3, 0.5, 0.92], [0, 0.42, 0.42, 1]);
  const wrongLen = useTransform(p, [0.28, 0.4, 0.5, 0.56], [0, 1, 1, 0]);
  const wrongOpacity = useTransform(p, [0.28, 0.32, 0.52, 0.58], [0, 1, 1, 0]);
  const [dot, setDot] = useState({ x: 70, y: 470 });
  const [step, setStep] = useState(0);

  useMotionValueEvent(p, "change", (v) => {
    const s = STEPS.reduce((acc, st, i) => (v >= st.at ? i : acc), 0);
    setStep(s);
    const path = pathRef.current;
    if (!path) return;
    if (v > 0.32 && v < 0.5) {
      // on the detour: the dot walks to the stairs and back
      const t = v < 0.42 ? (v - 0.32) / 0.1 : 1 - (v - 0.42) / 0.08;
      const k = Math.max(0, Math.min(1, t));
      setDot({ x: 330 + 190 * k, y: 300 });
      return;
    }
    const len = path.getTotalLength();
    const pt = path.getPointAtLength(len * routeLen.get());
    setDot({ x: pt.x, y: pt.y });
  });

  useEffect(() => {
    // first paint before any scroll
    const path = pathRef.current;
    if (path) {
      const pt = path.getPointAtLength(0);
      setDot({ x: pt.x, y: pt.y });
    }
  }, []);

  const detour = step === 1 || step === 2;

  return (
    <section ref={ref} id="route" className="ss-route" aria-labelledby="route-title">
      <div className="ss-route-stage">
        <div className="ss-route-copy">
          <h2 id="route-title" className="ss-display">
            Stickers, not GPS
          </h2>
          <ol className="ss-route-steps">
            {STEPS.map((s, i) => (
              <li key={s.title} aria-current={i === step ? "step" : undefined}>
                <motion.span
                  className="ss-route-tick"
                  animate={{ scaleY: i <= step ? 1 : 0.15, backgroundColor: i === step ? "#ffc013" : i < step ? "#f2f2f2" : "#3a3a3a" }}
                  transition={{ type: "spring", stiffness: 200, damping: 22 }}
                />
                <span>
                  <strong>{s.title}</strong>
                  <AnimatePresence initial={false}>
                    {i === step && (
                      <motion.span
                        className="ss-route-text"
                        initial={{ opacity: 0, height: 0 }}
                        animate={{ opacity: 1, height: "auto" }}
                        exit={{ opacity: 0, height: 0 }}
                        transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
                      >
                        {s.text}
                      </motion.span>
                    )}
                  </AnimatePresence>
                </span>
              </li>
            ))}
          </ol>
        </div>

        <div className="ss-route-plan">
          <svg viewBox="0 0 800 560" role="img" aria-label="Floor plan: the route from the gate past registration to the lift and Room 3, with a wrong turn towards the stairs re-routed">
            <defs>
              <pattern id="ss-dots" width="20" height="20" patternUnits="userSpaceOnUse">
                <circle cx="2" cy="2" r="1.2" fill="#2a2a2a" />
              </pattern>
            </defs>
            <rect width="800" height="560" fill="url(#ss-dots)" />
            {/* rooms */}
            <rect x="120" y="340" width="170" height="110" className="ss-room" />
            <rect x="380" y="340" width="190" height="110" className="ss-room" />
            <rect x="120" y="40" width="170" height="80" className="ss-room" />
            <rect x="380" y="40" width="200" height="80" className="ss-room" />
            <rect x="620" y="40" width="160" height="80" className="ss-room ss-room-goal" />
            <rect x="560" y="250" width="70" height="100" className="ss-room ss-room-stairs" />
            <text x="205" y="400" className="ss-room-label">Registration</text>
            <text x="475" y="400" className="ss-room-label">Pharmacy</text>
            <text x="205" y="85" className="ss-room-label">Lab</text>
            <text x="480" y="85" className="ss-room-label">Room 2</text>
            <text x="700" y="88" className="ss-room-label goal">Room 3</text>
            <text x="595" y="305" className="ss-room-label">Stairs</text>
            {/* corridors */}
            <path d="M70 470 L70 300 L700 300 M330 300 L330 150 L700 150" className="ss-corridor" />
            {/* wrong turn */}
            <motion.path d={WRONG} className="ss-wrong" style={{ pathLength: wrongLen, opacity: wrongOpacity }} />
            {/* the route */}
            <path ref={pathRef} d={ROUTE} fill="none" stroke="none" />
            <motion.path d={ROUTE} className="ss-route-line" style={{ pathLength: routeLen }} />
            {/* waypoints */}
            {WAYPOINTS.map((w) => (
              <g key={w.id}>
                <rect x={w.x - 9} y={w.y - 9} width="18" height="18" className={`ss-wp ${w.id === "W012" && detour ? "warn" : ""}`} />
                <text x={w.x} y={w.y + 30} className="ss-wp-id">
                  {w.id}
                </text>
              </g>
            ))}
            {/* the patient */}
            <motion.circle
              r="11"
              className="ss-patient"
              animate={{ cx: dot.x, cy: dot.y }}
              transition={{ type: "spring", stiffness: 300, damping: 30 }}
            />
            <motion.circle
              r="11"
              className="ss-patient-ring"
              animate={{ cx: dot.x, cy: dot.y, r: [11, 26], opacity: [0.7, 0] }}
              transition={{ cx: { type: "spring", stiffness: 300, damping: 30 }, cy: { type: "spring", stiffness: 300, damping: 30 }, r: { duration: 1.4, repeat: Infinity }, opacity: { duration: 1.4, repeat: Infinity } }}
            />
          </svg>
          <AnimatePresence>
            {step === 2 && (
              <motion.p
                className="ss-route-toast"
                initial={{ opacity: 0, y: 20, scale: 0.95 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -10 }}
                transition={{ type: "spring", stiffness: 260, damping: 20 }}
              >
                No problem. Let’s take a new route.
              </motion.p>
            )}
          </AnimatePresence>
        </div>
      </div>
    </section>
  );
}
