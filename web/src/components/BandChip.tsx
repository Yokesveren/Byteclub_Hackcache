import type { Band } from "@/lib/types";

// Colour is never the only signal: every band carries a word and an icon (PRD section 4).
// Pale tint with dark text of the same hue; a dashed edge means "suggested, not yet confirmed by staff".
const BANDS: Record<Band, { label: string; icon: string; className: string }> = {
  red: { label: "Emergency now", icon: "◆", className: "bg-[#c4242a] text-white border-[#c4242a]" },
  yellow: { label: "Priority", icon: "▲", className: "bg-[#fff1c2] text-[#5c3d00] border-[#d9a400]" },
  green: { label: "Routine", icon: "●", className: "bg-[#dcf5e4] text-[#0d3b1e] border-[#5fb47f]" },
};

export function BandChip({ band, provisional, size = "sm" }: { band: Band; provisional?: boolean; size?: "sm" | "lg" }) {
  const b = BANDS[band];
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border font-bold whitespace-nowrap ${b.className} ${
        size === "lg" ? "px-3 py-1 text-base" : "px-2 py-px text-xs"
      } ${provisional ? "border-dashed" : "border-transparent"}`}
      title={provisional ? `${b.label} (suggested, not yet confirmed by staff)` : `${b.label} (confirmed by staff)`}
    >
      <span aria-hidden>{b.icon}</span>
      {b.label}
      {provisional && <span className="font-normal opacity-80">· unconfirmed</span>}
    </span>
  );
}

const LEVELS = {
  green: { label: "Normal", icon: "●", dot: "bg-[#2f8a55]" },
  amber: { label: "Busy", icon: "▲", dot: "bg-[#d99a00]" },
  red: { label: "Overcrowded", icon: "■", dot: "bg-[#c4242a]" },
} as const;

/** Overcrowding: a coloured mark plus its word, never colour alone. */
export function LevelChip({ level, children }: { level: "green" | "amber" | "red"; children?: React.ReactNode }) {
  const l = LEVELS[level];
  return (
    <span className="inline-flex items-center gap-1.5 text-sm">
      <span className={`h-2 w-2 rounded-full ${l.dot}`} aria-hidden />
      {children}
      <span className={`font-bold ${level === "red" ? "text-[#a3161c]" : level === "amber" ? "text-[#7a5200]" : "text-[#1f6b3f]"}`}>{l.label}</span>
    </span>
  );
}
