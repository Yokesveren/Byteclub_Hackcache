import type { StaffRole } from "./types";

/** Where each role lands after signing in, and where it is sent back to when it opens a page it may not use. */
export const ROLE_HOME: Record<StaffRole, string> = {
  counter: "/counter",
  triage: "/facility",
  doctor: "/doctor",
  admin: "/district",
  demo: "/demo",
};

/** The staff screens, who may open them, and their tab titles. Order is the sidebar order. */
export const STAFF_ROUTES: { href: string; label: string; roles: StaffRole[] }[] = [
  { href: "/district", label: "District overview", roles: ["admin", "demo"] },
  { href: "/facility", label: "Facility", roles: ["triage", "admin", "demo"] },
  { href: "/doctor", label: "My patients", roles: ["doctor", "admin", "demo"] },
  { href: "/counter", label: "Counter", roles: ["counter", "triage", "admin", "demo"] },
  { href: "/flow", label: "Patient flow", roles: ["triage", "admin", "demo"] },
  { href: "/followups", label: "Follow-ups", roles: ["triage", "admin", "demo"] },
  { href: "/log", label: "Transparency log", roles: ["triage", "admin", "demo"] },
  { href: "/demo", label: "Demo controls", roles: ["demo", "admin"] },
];

export function routeFor(pathname: string) {
  return STAFF_ROUTES.find((r) => pathname === r.href || pathname.startsWith(`${r.href}/`));
}

export function canOpen(role: StaffRole, pathname: string): boolean {
  const r = routeFor(pathname);
  return !r || r.roles.includes(role);
}

/**
 * The `next` a sign-in may return to: a path on this site that the role can open.
 * Rejects absolute and protocol-relative URLs ("//evil.example"), so sign-in is never an open redirect.
 */
export function safeNext(next: string | null, role: StaffRole): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) return ROLE_HOME[role];
  if (next.startsWith("/login")) return ROLE_HOME[role];
  return canOpen(role, next) ? next : ROLE_HOME[role];
}
