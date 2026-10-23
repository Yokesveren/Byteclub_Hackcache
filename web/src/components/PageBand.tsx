"use client";

import { motion } from "motion/react";

export type BandTone = "sky" | "sand" | "mustard" | "magenta" | "night";

const TONES: Record<BandTone, { bg: string; ink: string; soft: string }> = {
  sky: { bg: "#cbe4f2", ink: "#0a0a0a", soft: "#2d4a58" },
  sand: { bg: "#f1e3b5", ink: "#0a0a0a", soft: "#4d4326" },
  mustard: { bg: "#ffc013", ink: "#0a0a0a", soft: "#4d3a00" },
  magenta: { bg: "#b0298f", ink: "#ffffff", soft: "#f6d7ee" },
  night: { bg: "#0a0a0a", ink: "#f2f2f2", soft: "#b4b4b4" },
};

/**
 * The top of every staff screen, in the landing page's language: a full-width field of one
 * chapter colour, the screen's name in black capitals, and the one finding under it.
 */
export function PageBand({
  tone,
  title,
  lead,
  aside,
  children,
}: {
  tone: BandTone;
  title: string;
  lead?: React.ReactNode;
  aside?: React.ReactNode;
  children?: React.ReactNode;
}) {
  const t = TONES[tone];
  return (
    <section
      className="relative overflow-hidden px-4 pb-6 pt-8 sm:px-8 sm:pt-10"
      style={{ background: t.bg, color: t.ink, ["--band-soft" as string]: t.soft }}
    >
      <div className="flex flex-wrap items-end justify-between gap-x-8 gap-y-3">
        <div className="min-w-0">
          <h1 className="overflow-hidden text-[clamp(2.5rem,1.6rem+3.4vw,4.75rem)] font-black uppercase leading-[0.88] tracking-[-0.05em]">
            <motion.span
              className="block"
              initial={{ y: "105%" }}
              animate={{ y: "0%" }}
              transition={{ type: "spring", stiffness: 160, damping: 20 }}
            >
              {title}
            </motion.span>
          </h1>
          {lead && (
            <motion.div
              className="mt-3 max-w-[60ch] text-lg leading-snug"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.15, type: "spring", stiffness: 160, damping: 22 }}
            >
              {lead}
            </motion.div>
          )}
        </div>
        {aside && <div className="text-sm" style={{ color: t.soft }}>{aside}</div>}
      </div>
      {children && (
        <motion.div
          className="mt-6"
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.25, type: "spring", stiffness: 160, damping: 22 }}
        >
          {children}
        </motion.div>
      )}
    </section>
  );
}

/** A big display figure with its label, set on a band. */
export function BandFigure({ label, value, unit, note }: { label: string; value: React.ReactNode; unit?: string; note?: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-sm" style={{ color: "var(--band-soft)" }}>
        {label}
      </dt>
      <dd className="mt-1 flex items-baseline gap-2 text-[clamp(2rem,1.4rem+1.8vw,3.25rem)] font-black leading-none tracking-[-0.04em] tabular-nums">
        {value}
        {unit && <span className="text-base font-bold tracking-normal">{unit}</span>}
        {note && <span className="text-sm font-bold tracking-normal underline decoration-2 underline-offset-4">{note}</span>}
      </dd>
    </div>
  );
}
