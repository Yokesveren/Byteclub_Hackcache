import { neighbours, route, stickersOn, type HospitalGraph } from "./graph";

/**
 * Demo visitors for the crowd map (simulated data, clearly labelled on screen). Each visitor walks a
 * real journey planned with the same router the app uses: gate → registration → waiting area →
 * their doctor's room → sometimes the pharmacy → out. Every sticker they pass is a scan event.
 */

export interface SimEvent {
  id: string;
  session_id: string;
  hospital_id: string;
  type: "entry" | "scan" | "room" | "wrong_turn" | "exit";
  waypoint_id: string;
  occurred_at: string;
}

interface Stop {
  id: string;
  /** ticks to stay after arriving */
  dwell: number;
  kind: "entry" | "scan" | "room" | "exit";
}

interface Visitor {
  session: string;
  stops: Stop[];
  at: number;
  wait: number;
}

const rand = (a: number, b: number) => a + Math.floor(Math.random() * (b - a + 1));
const pick = <T,>(xs: T[]) => xs[Math.floor(Math.random() * xs.length)];
const uuid = () => crypto.randomUUID();

export class FlowSim {
  private visitors: Visitor[] = [];
  // The places a visit passes through, read from the map's tags, so any hospital's map works.
  private rooms: string[];
  private registration: string;
  private pharmacy?: string;
  private waiting: Map<number, string>;

  constructor(private g: HospitalGraph) {
    const tagged = (tag: string) => g.nodes.filter((n) => n.sticker && n.tags.includes(tag));
    this.rooms = tagged("room").map((n) => n.id);
    this.registration = tagged("registration")[0]?.id ?? g.entrance;
    this.pharmacy = tagged("pharmacy")[0]?.id;
    this.waiting = new Map(tagged("waiting").map((n) => [n.floor, n.id]));
  }

  get count() {
    return this.visitors.length;
  }

  private plan(): Stop[] {
    const g = this.g;
    // General Medicine is the busiest department, so its rooms (named so on the map) draw more visitors.
    const gm = this.rooms.filter((id) => g.nodes.find((n) => n.id === id)?.name.en.includes("General Medicine"));
    const room = gm.length && Math.random() < 0.6 ? pick(gm) : pick(this.rooms);
    const floor = g.nodes.find((n) => n.id === room)?.floor ?? 0;
    const waiting = this.waiting.get(floor) ?? this.waiting.values().next().value;
    const avoidStairs = Math.random() < 0.35; // elderly, wheelchair, walking difficulty
    const legs: [string, number][] = [[this.registration, rand(3, 6)]];
    if (waiting) legs.push([waiting, rand(14, 30)]); // OPD waits are long: the waiting areas fill first
    legs.push([room, rand(3, 6)]);
    if (this.pharmacy && Math.random() < 0.55) legs.push([this.pharmacy, rand(2, 4)]);
    legs.push([g.entrance, 0]);

    const stops: Stop[] = [{ id: g.entrance, dwell: 1, kind: "entry" }];
    let from = g.entrance;
    for (const [to, dwell] of legs) {
      const path = route(g, from, to, { avoidStairs }) ?? route(g, from, to)!;
      const scans = stickersOn(g, path).slice(1);
      scans.forEach((id, i) => {
        const last = i === scans.length - 1;
        stops.push({
          id,
          dwell: last ? dwell : 1,
          kind: last && to === g.entrance ? "exit" : last && to === room ? "room" : "scan",
        });
      });
      from = to;
    }
    return stops;
  }

  /** Visitors already mid-visit when the simulation starts, so the map is busy at once. */
  warmStart(n: number, nowMs: number, hospitalId: string): SimEvent[] {
    const events: SimEvent[] = [];
    for (let i = 0; i < n; i++) {
      const stops = this.plan();
      const at = rand(1, stops.length - 2);
      const v: Visitor = { session: uuid(), stops, at, wait: rand(0, stops[at].dwell) };
      this.visitors.push(v);
      const t = nowMs - rand(2, 9) * 60_000;
      events.push(this.event(v, stops[0].id, "entry", t, hospitalId));
      events.push(this.event(v, stops[at].id, stops[at].kind === "room" ? "room" : "scan", nowMs - rand(0, 90) * 1000, hospitalId));
    }
    return events;
  }

  /** Advance every visitor one step; keep the building at about `target` people. */
  tick(nowMs: number, hospitalId: string, target: number): SimEvent[] {
    const events: SimEvent[] = [];
    const g = this.g;
    // A wrong turn is off-route inside the building; walking back out of the gate or into Emergency is not one.
    const stickers = new Set(g.nodes.filter((n) => n.sticker && !n.tags.includes("entrance") && !n.tags.includes("emergency")).map((n) => n.id));

    for (let spawn = 0; spawn < 3 && this.visitors.length < target; spawn++) {
      const stops = this.plan();
      const v: Visitor = { session: uuid(), stops, at: 0, wait: stops[0].dwell };
      this.visitors.push(v);
      // The simulation runs much faster than a real walk (a whole visit in about a minute), so the gate scan
      // is stamped a few minutes back: gate-to-room times then come out at a realistic 4 to 8 minutes.
      events.push(this.event(v, stops[0].id, "entry", nowMs - rand(3, 7) * 60_000, hospitalId));
    }

    for (const v of this.visitors) {
      if (v.wait > 0 && Math.random() < 0.85) {
        v.wait--;
        continue;
      }
      if (v.at >= v.stops.length - 1) continue;
      const here = v.stops[v.at].id;
      const next = v.stops[v.at + 1];
      // Now and then someone walks to the wrong sticker first, then is re-routed (PRD 9.5).
      if (next.kind === "scan" && Math.random() < 0.06) {
        const wrong = neighbours(g, here)
          .flatMap((n) => (stickers.has(n) ? [n] : neighbours(g, n)))
          .filter((n) => stickers.has(n) && n !== next.id && n !== here);
        if (wrong.length) {
          events.push(this.event(v, pick(wrong), "wrong_turn", nowMs, hospitalId));
          continue;
        }
      }
      v.at++;
      v.wait = next.dwell;
      events.push(this.event(v, next.id, next.kind, nowMs, hospitalId));
    }

    this.visitors = this.visitors.filter((v) => v.stops[v.at].kind !== "exit");
    return events;
  }

  private event(v: Visitor, waypoint: string, type: SimEvent["type"], ms: number, hospitalId: string): SimEvent {
    return { id: uuid(), session_id: `sim-${v.session}`, hospital_id: hospitalId, type, waypoint_id: waypoint, occurred_at: new Date(ms).toISOString() };
  }
}
