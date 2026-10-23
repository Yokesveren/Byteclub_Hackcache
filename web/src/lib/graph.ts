/**
 * The hospital as a graph (web/public/graph/H01.json) and the route planner the PRD describes (9.3):
 * Dijkstra over walkable edges, cost = distance plus small comfort penalties, stairs removed when
 * the patient avoids them. The patient app plans routes with the same rules.
 */

export interface GraphNode {
  id: string;
  floor: number;
  x: number;
  y: number;
  sticker: boolean;
  tags: string[];
  name: { en: string } & Record<string, string>;
  landmark: ({ en: string } & Record<string, string>) | null;
}

export interface GraphEdge {
  from: string;
  to: string;
  distance_m: number;
  stairs: boolean;
  lift: boolean;
  ramp: boolean;
  crowd_prone: boolean;
}

export interface GraphArea {
  id: string;
  floor: number;
  label: string;
  kind: string;
  x: number;
  y: number;
  w: number;
  h: number;
  capacity: number;
  waypoints: string[];
}

export interface HospitalGraph {
  hospital_id: string;
  name: string;
  version: number;
  floors: { floor: number; name: string; width: number; height: number }[];
  entrance: string;
  emergency: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
  areas: GraphArea[];
}

export interface RouteOptions {
  avoidStairs?: boolean;
  /** Emergency routing takes the shortest path and ignores comfort penalties (PRD 9.3). */
  emergency?: boolean;
}

const LIFT_WAIT_M = 10; // waiting for the lift, in metres of walking
const CROWD_PENALTY_M = 3; // a calmer route may win when it is only slightly longer

function cost(e: GraphEdge, o: RouteOptions): number {
  if (o.emergency) return e.distance_m;
  return e.distance_m + (e.lift ? LIFT_WAIT_M : 0) + (e.crowd_prone ? CROWD_PENALTY_M : 0);
}

/** Shortest walkable path from `from` to `to`, or null when none exists (e.g. stairs only, avoiding stairs). */
export function route(g: HospitalGraph, from: string, to: string, o: RouteOptions = {}): string[] | null {
  const adj = new Map<string, { to: string; w: number }[]>();
  for (const n of g.nodes) adj.set(n.id, []);
  for (const e of g.edges) {
    if (o.avoidStairs && e.stairs) continue;
    const w = cost(e, o);
    adj.get(e.from)!.push({ to: e.to, w });
    adj.get(e.to)!.push({ to: e.from, w });
  }
  const dist = new Map<string, number>([[from, 0]]);
  const prev = new Map<string, string>();
  const done = new Set<string>();
  // The graph is small (tens of nodes): a linear scan for the next node is fast enough.
  while (true) {
    let u: string | null = null;
    let best = Infinity;
    for (const [id, d] of dist) if (!done.has(id) && d < best) [u, best] = [id, d];
    if (u === null) return null;
    if (u === to) break;
    done.add(u);
    for (const { to: v, w } of adj.get(u) ?? []) {
      const nd = best + w;
      if (nd < (dist.get(v) ?? Infinity)) {
        dist.set(v, nd);
        prev.set(v, u);
      }
    }
  }
  const path = [to];
  while (path[0] !== from) path.unshift(prev.get(path[0])!);
  return path;
}

/** The printed stickers along a path: what a patient actually scans. */
export function stickersOn(g: HospitalGraph, path: string[]): string[] {
  const byId = new Map(g.nodes.map((n) => [n.id, n]));
  return path.filter((id) => byId.get(id)?.sticker);
}

export function neighbours(g: HospitalGraph, id: string): string[] {
  return g.edges.flatMap((e) => (e.from === id ? [e.to] : e.to === id ? [e.from] : []));
}
