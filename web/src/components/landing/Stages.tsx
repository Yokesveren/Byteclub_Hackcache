"use client";

import { AnimatePresence, motion, useMotionValueEvent, useScroll, useTransform } from "motion/react";
import { useRef, useState } from "react";
import { scrollToY, Tilt } from "./fx";

// The patient's first four minutes, as the app shows them. Sample patient: Kamla, 62.
const STAGES = [
  {
    name: "Language",
    summary: "Taps her own script",
    clause: "Her own language",
    text: "Five cards, each in its own script, each with a recorded greeting. Kamla taps Kashmiri and every screen after this speaks it.",
  },
  {
    name: "Questions",
    summary: "Seven simple steps",
    clause: "One question a screen",
    text: "Seven steps with a progress bar, picture cards and a Listen button. Voice answers are read back before they count.",
  },
  {
    name: "Token",
    summary: "M-017 and a time window",
    clause: "A token and a window",
    text: "M-017, Room 3, first floor, six ahead, likely between 11:20 and 11:50. Priority stays provisional until a nurse confirms it.",
  },
  {
    name: "Room 3",
    summary: "Stickers lead the way",
    clause: "Follow the stickers",
    text: "Each printed QR sticker tells the phone where she is. A wrong turn re-routes her, through the lift, never the stairs.",
  },
];

/**
 * The journey as a flow: the section pins and scrolling walks through the four stages.
 * The stepper shows the whole journey at once; any step can also be tapped.
 */
export function Stages() {
  const ref = useRef<HTMLElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start start", "end end"] });
  const [stage, setStage] = useState(0);
  const [dir, setDir] = useState(1);
  // progress inside the current stage, for the bar under the active step
  const within = useTransform(scrollYProgress, (v) => Math.min(1, Math.max(0, v * STAGES.length - Math.min(STAGES.length - 1, Math.floor(v * STAGES.length)))));

  useMotionValueEvent(scrollYProgress, "change", (v) => {
    const next = Math.min(STAGES.length - 1, Math.floor(v * STAGES.length));
    if (next !== stage) {
      setDir(next > stage ? 1 : -1);
      setStage(next);
    }
  });

  const jump = (i: number) => {
    const el = ref.current;
    if (!el) return;
    const total = el.offsetHeight - window.innerHeight;
    scrollToY(el.offsetTop + ((i + 0.35) / STAGES.length) * total);
  };

  const s = STAGES[stage];

  return (
    <section ref={ref} id="flow" className="ss-flow" aria-labelledby="flow-title">
      <div className="ss-flow-stage">
        <div className="ss-flow-top">
          <h2 id="flow-title" className="ss-flow-title">
            Kamla’s first four minutes
          </h2>
          <p className="ss-flow-hint">
            <motion.span
              className="ss-flow-mouse"
              aria-hidden
              animate={{ y: [0, 6, 0] }}
              transition={{ duration: 1.4, repeat: Infinity, ease: "easeInOut" }}
            />
            Scroll to walk through it, or tap a step
          </p>
        </div>

        <ol className="ss-steps" aria-label="The patient's journey">
          {STAGES.map((st, i) => {
            const state = i < stage ? "done" : i === stage ? "now" : "next";
            return (
              <li key={st.name} className={`ss-step ${state}`}>
                <button type="button" onClick={() => jump(i)} aria-current={i === stage ? "step" : undefined}>
                  <motion.span
                    className="ss-step-dot"
                    animate={{ scale: i === stage ? 1.15 : 1 }}
                    transition={{ type: "spring", stiffness: 400, damping: 20 }}
                  >
                    {i < stage ? "✓" : i + 1}
                  </motion.span>
                  <span className="ss-step-words">
                    <strong>{st.name}</strong>
                    <span>{st.summary}</span>
                  </span>
                </button>
                <span className="ss-step-bar" aria-hidden>
                  <motion.span style={{ scaleX: i < stage ? 1 : i === stage ? within : 0 }} />
                </span>
                {i < STAGES.length - 1 && (
                  <span className="ss-step-arrow" aria-hidden>
                    →
                  </span>
                )}
              </li>
            );
          })}
        </ol>

        <div className="ss-flow-body">
          <div className="ss-flow-copy">
            <AnimatePresence mode="wait" initial={false}>
              <motion.div
                key={stage}
                initial={{ opacity: 0, y: 40 * dir, filter: "blur(10px)" }}
                animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
                exit={{ opacity: 0, y: -40 * dir, filter: "blur(10px)" }}
                transition={{ type: "spring", stiffness: 260, damping: 26 }}
              >
                <p className="ss-flow-count">
                  Step {stage + 1} of {STAGES.length}
                </p>
                <h3 className="ss-display ss-flow-clause" aria-live="polite">
                  {s.clause}
                </h3>
                <p className="ss-flow-text">{s.text}</p>
              </motion.div>
            </AnimatePresence>
          </div>
          <div className="ss-flow-phone">
            <Tilt className="ss-fragment-wrap">
              <AnimatePresence mode="popLayout" initial={false}>
                <motion.div
                  key={stage}
                  initial={{ opacity: 0, x: 120 * dir, rotateY: -25 * dir, scale: 0.9 }}
                  animate={{ opacity: 1, x: 0, rotateY: 0, scale: 1 }}
                  exit={{ opacity: 0, x: -120 * dir, rotateY: 25 * dir, scale: 0.9 }}
                  transition={{ type: "spring", stiffness: 200, damping: 24 }}
                  style={{ transformPerspective: 1000 }}
                >
                  {FRAGMENTS[stage]}
                </motion.div>
              </AnimatePresence>
            </Tilt>
          </div>
        </div>
      </div>
    </section>
  );
}

function AppTop({ step }: { step: string }) {
  return (
    <div className="ss-app-top">
      <span>Chinar District Hospital</span>
      <span>{step}</span>
    </div>
  );
}

function Sos() {
  return <div className="ss-app-sos">Emergency · 112 · 108</div>;
}

const FRAGMENTS = [
  <div className="ss-app" data-ui key="lang">
    <AppTop step="Offline ready" />
    <h3>Choose your language</h3>
    <div className="ss-langs">
      <span className="on" lang="ks" dir="rtl">
        کٲشُر <small dir="ltr">Kashmiri</small>
      </span>
      <span lang="ur" dir="rtl">
        اُردُو <small dir="ltr">Urdu</small>
      </span>
      <span lang="doi">
        डोगरी <small>Dogri</small>
      </span>
      <span lang="hi">
        हिन्दी <small>Hindi</small>
      </span>
    </div>
    <div className="ss-listen">
      <span>English</span>
      <span>▶ Listen</span>
    </div>
    <Sos />
  </div>,
  <div className="ss-app" data-ui key="ask">
    <AppTop step="Step 4 of 7" />
    <div className="ss-progress" role="img" aria-label="Step 4 of 7">
      <i />
    </div>
    <h3>How old are you?</h3>
    <div className="ss-ages">
      <span>
        <AgeIcon kind="child" />
        Child <small>1 to 17</small>
      </span>
      <span>
        <AgeIcon kind="adult" />
        Adult <small>18 to 59</small>
      </span>
      <span className="on">
        <AgeIcon kind="elderly" />
        Elderly <small>60 and over</small>
      </span>
    </div>
    <div className="ss-listen">
      <span>▶ Listen</span>
      <span>Speak</span>
    </div>
    <Sos />
  </div>,
  <div className="ss-app" data-ui key="slip">
    <AppTop step="Last updated 10:42" />
    <div className="ss-slip-row">
      <div>
        <div style={{ fontSize: 14, color: "#5f6368" }}>Token</div>
        <div className="ss-slip-token">M-017</div>
        <div style={{ fontSize: 22, fontWeight: 700, marginTop: 6 }}>Kamla</div>
      </div>
      <Qr />
    </div>
    <div style={{ marginTop: 10 }}>
      <span className="ss-pill priority">▲ Priority · provisional</span>
    </div>
    <p className="ss-slip-room">
      <b>Room 3</b>, first floor · lift and ramp
      <br />
      <span style={{ fontSize: 14, color: "#5f6368" }}>General Medicine</span>
    </p>
    <div className="ss-slip-wait">
      <b>6 ahead of you</b>
      <br />
      Likely between <b>11:20</b> and <b>11:50</b>
      <small>Feeling worse? Press Urgent help.</small>
    </div>
  </div>,
  <div className="ss-app" data-ui key="nav">
    <AppTop step="Avoiding stairs" />
    <FloorPlan />
    <p className="ss-nav-step">Go straight to the pharmacy window, then turn left at the green notice board.</p>
    <p className="ss-nav-hint">Scan the sticker at the end of this corridor.</p>
    <div className="ss-nav-actions">
      <span>Scan sticker</span>
      <span>I’m lost</span>
    </div>
  </div>,
];

function AgeIcon({ kind }: { kind: "child" | "adult" | "elderly" }) {
  const h = kind === "child" ? 16 : 22;
  return (
    <svg viewBox="0 0 30 30" aria-hidden fill="currentColor">
      <circle cx="15" cy={30 - h - 5} r="4" />
      <rect x="11" y={30 - h} width="8" height={h} rx="3" />
      {kind === "elderly" && <rect x="22" y="12" width="2" height="18" rx="1" />}
    </svg>
  );
}

/** A real QR is printed on the slip; here a fixed pattern stands in at true size. */
function Qr() {
  const cells = [
    "1111111010111111",
    "1000001011100001",
    "1011101000101101",
    "1011101101101101",
    "1000001010100001",
    "1111111010111111",
    "0000000011000000",
    "1101011100110110",
    "0110100101011011",
    "1011011010010100",
    "0000000010111010",
    "1111111001010110",
    "1000001011011001",
    "1011101010101111",
    "1000001001110010",
    "1111111010011011",
  ];
  return (
    <svg className="ss-qr" viewBox="0 0 16 16" role="img" aria-label="Slip QR code" shapeRendering="crispEdges">
      {cells.flatMap((row, y) => [...row].map((c, x) => (c === "1" ? <rect key={`${x}-${y}`} x={x} y={y} width="1" height="1" /> : null)))}
    </svg>
  );
}

/** First floor of the demo hospital: from the lift (you are here) along the corridor to Room 3; the stairs are crossed out. */
function FloorPlan() {
  return (
    <svg className="ss-nav-map" viewBox="0 0 360 210" role="img" aria-label="First floor plan: route from the lift along the corridor to Room 3, stairs avoided">
      <rect x="0" y="0" width="360" height="210" fill="#f1f4f6" />
      <rect x="16" y="92" width="328" height="34" fill="#e3e9ee" />
      <rect x="16" y="16" width="96" height="68" fill="#dde3e8" />
      <rect x="122" y="16" width="96" height="68" fill="#dde3e8" />
      <rect x="228" y="16" width="116" height="68" fill="#0a0a0a" />
      <text x="64" y="55" fill="#44606e" fontSize="14" textAnchor="middle">Room 1</text>
      <text x="170" y="55" fill="#44606e" fontSize="14" textAnchor="middle">Pharmacy</text>
      <text x="286" y="56" fill="#ffffff" fontSize="18" fontWeight="700" textAnchor="middle">Room 3</text>
      <rect x="16" y="134" width="96" height="60" fill="#dde3e8" />
      <text x="64" y="186" fill="#44606e" fontSize="14" textAnchor="middle">Lift</text>
      <rect x="122" y="134" width="96" height="60" fill="#eceff1" />
      <text x="170" y="186" fill="#8a96a0" fontSize="14" textAnchor="middle">Stairs</text>
      <path d="M152 146 L188 170 M152 170 L188 146" stroke="#c4242a" strokeWidth="3" />
      <path d="M64 150 L64 109 L286 109 L286 84" fill="none" stroke="#b0298f" strokeWidth="6" strokeLinejoin="round" strokeLinecap="round" />
      <circle cx="64" cy="150" r="9" fill="#b0298f" />
      <circle cx="64" cy="150" r="4" fill="#ffffff" />
      <rect x="166" y="102" width="14" height="14" fill="#ffc013" />
      <text x="234" y="152" fill="#44606e" fontSize="13">You are here</text>
      <text x="234" y="170" fill="#44606e" fontSize="13">Sticker on the route</text>
      <rect x="220" y="160" width="10" height="10" fill="#ffc013" />
      <circle cx="225" cy="148" r="5" fill="#b0298f" />
    </svg>
  );
}
