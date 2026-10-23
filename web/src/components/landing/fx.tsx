"use client";

import {
  animate,
  motion,
  MotionConfig,
  useInView,
  useMotionValue,
  useScroll,
  useSpring,
  useTransform,
} from "motion/react";
import Lenis from "lenis";
import "lenis/dist/lenis.css";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";

let lenis: Lenis | null = null;

/** Scroll the page to a y position, through the smooth scroller when it is running. */
export function scrollToY(y: number) {
  if (lenis) lenis.scrollTo(y, { duration: 1.1 });
  else window.scrollTo({ top: y, behavior: "smooth" });
}

/**
 * Inertial smooth scrolling for the landing page, with in-page links that glide to their section.
 * Off for anyone who asks for reduced motion.
 */
export function SmoothScroll() {
  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    lenis = new Lenis({ autoRaf: true, anchors: { offset: 0, duration: 1.2 }, lerp: 0.1 });
    return () => {
      lenis?.destroy();
      lenis = null;
    };
  }, []);
  return null;
}

/** Motion follows the visitor's "reduce motion" setting everywhere on the page. */
export function MotionRoot({ children }: { children: React.ReactNode }) {
  return <MotionConfig reducedMotion="user">{children}</MotionConfig>;
}

/** A thin accent line along the top edge that fills as the page is read. */
export function ScrollProgress() {
  const { scrollYProgress } = useScroll();
  const scaleX = useSpring(scrollYProgress, { stiffness: 180, damping: 30, mass: 0.3 });
  return <motion.div className="ss-progress-bar" style={{ scaleX }} aria-hidden />;
}

const MotionLink = motion.create(Link);

/** A link that leans toward the pointer and settles back with a spring. */
export function Magnetic({
  href,
  className,
  children,
  strength = 0.35,
}: {
  href: string;
  className?: string;
  children: React.ReactNode;
  strength?: number;
}) {
  const ref = useRef<HTMLAnchorElement>(null);
  const x = useSpring(0, { stiffness: 260, damping: 18 });
  const y = useSpring(0, { stiffness: 260, damping: 18 });

  return (
    <MotionLink
      ref={ref}
      href={href}
      className={className}
      style={{ x, y }}
      onPointerMove={(e: React.PointerEvent<HTMLAnchorElement>) => {
        const r = ref.current?.getBoundingClientRect();
        if (!r) return;
        x.set((e.clientX - (r.left + r.width / 2)) * strength);
        y.set((e.clientY - (r.top + r.height / 2)) * strength);
      }}
      onPointerLeave={() => {
        x.set(0);
        y.set(0);
      }}
      whileTap={{ scale: 0.96 }}
    >
      {children}
    </MotionLink>
  );
}

/** Counts up to a figure the first time it scrolls into view. */
export function CountUp({ to, duration = 1.6, className }: { to: number; duration?: number; className?: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const inView = useInView(ref, { once: true, margin: "0px 0px -15% 0px" });
  const [value, setValue] = useState(to);

  useEffect(() => {
    if (!inView) return;
    const controls = animate(0, to, {
      duration,
      ease: [0.16, 1, 0.3, 1],
      onUpdate: (v) => setValue(Math.round(v)),
    });
    return () => controls.stop();
  }, [inView, to, duration]);

  return (
    <span ref={ref} className={className}>
      {value.toLocaleString("en-IN")}
    </span>
  );
}

/** Children rise in one after another when the block enters the screen. */
export function Stagger({
  children,
  className,
  as = "div",
  delay = 0,
}: {
  children: React.ReactNode;
  className?: string;
  as?: "div" | "ul" | "section";
  delay?: number;
}) {
  const Comp = motion[as];
  return (
    <Comp
      className={className}
      initial="hidden"
      whileInView="show"
      viewport={{ once: true, amount: 0.15 }}
      variants={{ hidden: {}, show: { transition: { staggerChildren: 0.08, delayChildren: delay } } }}
    >
      {children}
    </Comp>
  );
}

export const riseItem = {
  hidden: { opacity: 0, y: 28, filter: "blur(6px)" },
  show: { opacity: 1, y: 0, filter: "blur(0px)", transition: { type: "spring", stiffness: 120, damping: 20 } },
} as const;

export function Rise({ children, className, as = "div" }: { children: React.ReactNode; className?: string; as?: "div" | "p" | "h2" | "h3" | "li" }) {
  const Comp = motion[as];
  return (
    <Comp className={className} variants={riseItem}>
      {children}
    </Comp>
  );
}

/** A picture that drifts against the scroll, slower than the page around it. */
export function Parallax({
  children,
  className,
  distance = 80,
  scale = [1.08, 1],
}: {
  children: React.ReactNode;
  className?: string;
  distance?: number;
  scale?: [number, number];
}) {
  const ref = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start end", "end start"] });
  const y = useTransform(scrollYProgress, [0, 1], [distance, -distance]);
  const s = useTransform(scrollYProgress, [0, 0.5], scale);
  return (
    <motion.div ref={ref} className={className} style={{ y, scale: s }}>
      {children}
    </motion.div>
  );
}

/** Tilts its child in 3D toward the pointer, like a phone held up to the light. */
export function Tilt({ children, className, max = 10 }: { children: React.ReactNode; className?: string; max?: number }) {
  const ref = useRef<HTMLDivElement>(null);
  const px = useMotionValue(0.5);
  const py = useMotionValue(0.5);
  const rotateY = useSpring(useTransform(px, [0, 1], [-max, max]), { stiffness: 150, damping: 15 });
  const rotateX = useSpring(useTransform(py, [0, 1], [max, -max]), { stiffness: 150, damping: 15 });

  return (
    <motion.div
      ref={ref}
      className={className}
      style={{ rotateX, rotateY, transformPerspective: 900 }}
      onPointerMove={(e) => {
        const r = ref.current?.getBoundingClientRect();
        if (!r) return;
        px.set((e.clientX - r.left) / r.width);
        py.set((e.clientY - r.top) / r.height);
      }}
      onPointerLeave={() => {
        px.set(0.5);
        py.set(0.5);
      }}
    >
      {children}
    </motion.div>
  );
}

/** An endless band of words that slides sideways; duplicated once so the loop has no seam. */
export function Marquee({ items, speed = 28, className }: { items: { text: string; lang?: string; dir?: "rtl" | "ltr" }[]; speed?: number; className?: string }) {
  const row = (
    <span className="ss-marquee-row">
      {items.map((it, i) => (
        <span key={i} lang={it.lang} dir={it.dir} className="ss-marquee-item">
          {it.text}
        </span>
      ))}
    </span>
  );
  return (
    <div className={`ss-marquee ${className ?? ""}`}>
      <motion.div
        className="ss-marquee-track"
        animate={{ x: ["0%", "-50%"] }}
        transition={{ duration: speed, ease: "linear", repeat: Infinity }}
      >
        {row}
        <span aria-hidden>{row}</span>
      </motion.div>
    </div>
  );
}
