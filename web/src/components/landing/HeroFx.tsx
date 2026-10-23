"use client";

import { motion, useMotionValue, useScroll, useSpring, useTransform } from "motion/react";
import Link from "next/link";
import { useRef } from "react";
import { Magnetic } from "./fx";

// A hospital as a graph: QR-sticker waypoints (squares) joined by corridors. Coordinates in a 1440 x 900 box.
const NODES: [number, number][] = [
  [110, 150], [330, 110], [560, 190], [880, 120], [1130, 170], [1330, 110],
  [220, 330], [700, 190], [1240, 330],
  [140, 620], [400, 600], [720, 585], [1050, 605], [1320, 640],
];
const EDGES: [number, number][] = [
  [0, 1], [1, 2], [2, 3], [3, 4], [4, 5], [0, 6], [6, 7], [2, 7], [7, 3], [4, 8],
  [6, 9], [9, 10], [10, 11], [11, 12], [12, 13], [8, 13],
];
// The route a patient takes, kept clear of the headline: gate -> corridor -> lift -> Room 3.
const ROUTE = [9, 10, 11, 12, 13, 8, 4, 5];
const routePath = ROUTE.map((n, i) => `${i ? "L" : "M"}${NODES[n][0]} ${NODES[n][1]}`).join(" ");

const LINES = ["Nobody", "gets lost"];

export function HeroFx() {
  const ref = useRef<HTMLElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start start", "end start"] });
  const titleY = useTransform(scrollYProgress, [0, 1], [0, 160]);
  const titleOpacity = useTransform(scrollYProgress, [0, 0.7], [1, 0]);
  const netY = useTransform(scrollYProgress, [0, 1], [0, -120]);

  // The network leans a little toward the pointer.
  const mx = useMotionValue(0);
  const my = useMotionValue(0);
  const sx = useSpring(mx, { stiffness: 60, damping: 20 });
  const sy = useSpring(my, { stiffness: 60, damping: 20 });
  const netShift = useTransform(() => sy.get() + netY.get());

  let letter = 0;

  return (
    <header
      ref={ref}
      className="ss-hero"
      onPointerMove={(e) => {
        mx.set((e.clientX / window.innerWidth - 0.5) * -24);
        my.set((e.clientY / window.innerHeight - 0.5) * -16);
      }}
    >
      <motion.svg
        className="ss-hero-net"
        viewBox="0 0 1440 900"
        preserveAspectRatio="xMidYMid slice"
        aria-hidden
        style={{ x: sx, y: netShift }}
      >
        {EDGES.map(([a, b], i) => (
          <motion.line
            key={i}
            x1={NODES[a][0]}
            y1={NODES[a][1]}
            x2={NODES[b][0]}
            y2={NODES[b][1]}
            className="ss-net-edge"
            initial={{ pathLength: 0, opacity: 0 }}
            animate={{ pathLength: 1, opacity: 1 }}
            transition={{ duration: 1.2, delay: 0.3 + i * 0.06, ease: [0.16, 1, 0.3, 1] }}
          />
        ))}
        <motion.path
          d={routePath}
          className="ss-net-route"
          initial={{ pathLength: 0 }}
          animate={{ pathLength: [0, 1, 1], opacity: [1, 1, 0] }}
          transition={{ duration: 4.2, delay: 1.4, times: [0, 0.75, 1], repeat: Infinity, repeatDelay: 0.8, ease: "easeInOut" }}
        />
        {NODES.map(([x, y], i) => (
          <motion.rect
            key={i}
            x={x - 7}
            y={y - 7}
            width={14}
            height={14}
            className={ROUTE.includes(i) ? "ss-net-node on" : "ss-net-node"}
            initial={{ scale: 0, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ type: "spring", stiffness: 300, damping: 14, delay: 0.2 + i * 0.05 }}
            style={{ transformOrigin: `${x}px ${y}px` }}
          />
        ))}
      </motion.svg>

      <motion.div
        className="ss-hero-bar"
        initial={{ opacity: 0, y: -16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.7, delay: 0.2, ease: [0.16, 1, 0.3, 1] }}
      >
        <Link href="/" className="ss-wordmark" aria-label="SevaSetu home">
          <svg className="ss-mark" viewBox="0 0 30 30" aria-hidden>
            <rect width="30" height="30" fill="#b0298f" />
            <path d="M5 21 Q15 5 25 21" fill="none" stroke="#ffffff" strokeWidth="3.4" />
            <rect x="4" y="21" width="22" height="3.4" fill="#ffffff" />
          </svg>
          SevaSetu
        </Link>
        <Magnetic href="/login" className="ss-btn">
          Open the dashboard
        </Magnetic>
      </motion.div>

      <motion.h1 className="ss-display ss-hero-title" style={{ y: titleY, opacity: titleOpacity }}>
        {LINES.map((line, li) => (
          <span key={line} className="ss-line">
            {Array.from(line).map((ch) => {
              const i = letter++;
              const glyph = ch === " " ? " " : ch;
              return (
                <motion.span
                  key={i}
                  // second line sits on the waterline: each letter draws its own ink half below the seam
                  className={li === 1 ? "ss-letter sink" : "ss-letter"}
                  data-ch={glyph}
                  initial={{ y: "110%", rotate: 8 }}
                  animate={{ y: "0%", rotate: 0 }}
                  transition={{ type: "spring", stiffness: 220, damping: 18, delay: 0.15 + i * 0.035 }}
                >
                  {glyph}
                </motion.span>
              );
            })}
          </span>
        ))}
      </motion.h1>

      <motion.p
        className="ss-hero-sub"
        initial={{ opacity: 0, y: 24, filter: "blur(8px)" }}
        animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
        transition={{ duration: 0.9, delay: 1.1, ease: [0.16, 1, 0.3, 1] }}
      >
        SevaSetu is the queue and the way in for government hospitals in Jammu and Kashmir: register in Kashmiri, Urdu,
        Dogri, Hindi or English, get a token, and follow printed stickers to the right room, even with no signal.
      </motion.p>

      <motion.div
        className="ss-scroll-cue"
        aria-hidden
        initial={{ opacity: 0 }}
        animate={{ opacity: 1, y: [0, 10, 0] }}
        transition={{ opacity: { delay: 2 }, y: { duration: 1.6, repeat: Infinity, ease: "easeInOut" } }}
      />
    </header>
  );
}
