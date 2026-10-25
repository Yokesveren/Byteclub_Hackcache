"use client";

import { AnimatePresence, motion } from "motion/react";
import { useEffect, useRef, useState } from "react";
import { BandFigure, PageBand } from "@/components/PageBand";
import { useShell } from "@/components/StaffShell";
import { hhmm } from "@/lib/format";
import { useLiveData } from "@/lib/hooks";
import { rpc, supabase } from "@/lib/supabase";

interface Sms {
  id: string;
  phone: string;
  direction: "in" | "out";
  kind: string;
  body: string;
  status: string;
  created_at: string;
  token: string | null;
}

interface ChatQuestion {
  question: string;
  outcome: "emergency" | "refused" | "answer" | "live" | "place" | "help_desk";
  faq_id: string | null;
  language: string;
  created_at: string;
}

const KIND: Record<string, string> = {
  text: "Text", missed_call: "Missed call", reply: "Reply", menu: "Menu", registered: "Registered", urgent: "Urgent help",
  near_turn: "Turn near", called: "Called", emergency: "Sent to Emergency", reminder: "Follow-up reminder", followup: "Missed follow-up",
};
const OUTCOME: Record<ChatQuestion["outcome"], { word: string; bg: string; ink: string }> = {
  emergency: { word: "Sent to Emergency", bg: "#c4242a", ink: "#ffffff" },
  refused: { word: "Refused: medical advice", bg: "#fff1c2", ink: "#5c3d00" },
  answer: { word: "Approved answer", bg: "#dcf5e4", ink: "#0d3b1e" },
  live: { word: "Live hospital fact", bg: "#dcf5e4", ink: "#0d3b1e" },
  place: { word: "Showed the way", bg: "#dcecf7", ink: "#0b2a3c" },
  help_desk: { word: "Sent to help desk", bg: "#edf3f8", ink: "#3e5566" },
};
const PRESETS = ["SLIP", "REG 1 Asha 34", "HELP feeling dizzy"];
const masked = (p: string) => `•••• ${p.slice(-4)}`;

export default function MessagesView() {
  const { hospitalId, staff } = useShell();
  const [phone, setPhone] = useState("9876500001");
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const [number, setNumber] = useState<string | null>(null);
  const screen = useRef<HTMLDivElement>(null);

  const sms = useLiveData(() => rpc<Sms[]>("get_sms", { p_hospital: hospitalId, p_limit: 150 }), {
    hospitalId,
    tables: ["sms_messages"],
    pollMs: 20000,
  });
  const chat = useLiveData(() => rpc<ChatQuestion[]>("get_chat_log", { p_hospital: hospitalId, p_limit: 60 }), {
    hospitalId,
    tables: ["chat_log"],
    pollMs: 30000,
  });

  useEffect(() => {
    let live = true;
    supabase.from("hospitals").select("phone").eq("id", hospitalId).single()
      .then(({ data }) => live && setNumber((data as { phone: string | null } | null)?.phone ?? null));
    return () => {
      live = false;
    };
  }, [hospitalId]);

  const all = sms.data ?? [];
  const thread = all.filter((m) => m.phone === phone.replace(/\D/g, "").slice(-10)).slice().reverse();
  const today = all.filter((m) => new Date(m.created_at).toDateString() === new Date(all[0]?.created_at ?? 0).toDateString());
  // The phone shows its newest text, like a real one.
  useEffect(() => {
    screen.current?.scrollTo({ top: screen.current.scrollHeight, behavior: "smooth" });
  }, [thread.length]);
  const canDemo = staff.role === "demo" || staff.role === "admin" || staff.role === "triage" || staff.role === "counter";

  const send = async (body: string) => {
    if (!body.trim()) return;
    setBusy(true);
    setNote(null);
    try {
      await rpc("sms_receive", { p: { hospital_id: hospitalId, from: phone, body } });
      setText("");
      await sms.refresh();
    } catch (e) {
      setNote(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };
  const missedCall = async () => {
    setBusy(true);
    try {
      await rpc("sms_missed_call", { p: { hospital_id: hospitalId, from: phone } });
      await sms.refresh();
    } finally {
      setBusy(false);
    }
  };
  const reminders = async () => {
    setBusy(true);
    try {
      const n = await rpc<number>("sms_followup_reminders");
      setNote(n === 0 ? "No follow-ups due in the next two days need a reminder." : `Sent ${n} follow-up ${n === 1 ? "reminder" : "reminders"}.`);
      await sms.refresh();
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <PageBand
        tone="linen"
        title="Messages"
        lead={
          <>
            Patients without data text <b>{number ?? "the hospital number"}</b>: SLIP for their place, REG to register, HELP when they feel
            worse. Texts go out by themselves when 3 are ahead, when called, and before a follow-up. Demo gateway: nothing leaves this
            system.
          </>
        }
      >
        <dl className="grid grid-cols-2 gap-x-8 gap-y-4 sm:max-w-3xl sm:grid-cols-4">
          <BandFigure label="Texts in today" value={today.filter((m) => m.direction === "in").length} />
          <BandFigure label="Texts out today" value={today.filter((m) => m.direction === "out").length} />
          <BandFigure label="Turn-near alerts" value={today.filter((m) => m.kind === "near_turn").length} />
          <BandFigure label="Urgent by SMS" value={today.filter((m) => m.kind === "urgent").length} />
        </dl>
      </PageBand>

      <div className="grid gap-6 px-4 py-6 sm:px-8 xl:grid-cols-[380px_1fr]">
        {/* A basic phone, for trying the SMS fallback */}
        {canDemo && (
          <section aria-labelledby="phone-title" className="self-start rounded-[32px] bg-[#0b2a3c] p-4 text-white">
            <div className="flex items-baseline justify-between px-2 pb-3">
              <h2 id="phone-title" className="text-lg font-black uppercase tracking-[-0.02em]">A basic phone</h2>
              <span className="text-xs text-[#9fe5cf]">demo</span>
            </div>
            <label className="block px-2 text-xs font-bold text-[#c9ece6]">
              This phone&apos;s number
              <input
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                inputMode="tel"
                className="mt-1 w-full rounded-xl bg-white/10 px-3 py-2 text-base font-bold tabular-nums text-white outline-none focus:ring-2 focus:ring-[#9fe5cf]"
              />
            </label>
            <div ref={screen} className="mt-3 flex h-[340px] flex-col gap-2 overflow-y-auto rounded-2xl bg-[#e8efe9] p-3 text-[#0b2a3c]" aria-live="polite">
              {thread.length === 0 && <p className="m-auto max-w-[24ch] text-center text-sm text-[#3e5566]">No texts yet. Send SLIP, or give a missed call.</p>}
              {thread.map((m) => (
                <div
                  key={m.id}
                  className={`max-w-[85%] rounded-2xl px-3 py-2 text-sm leading-snug ${m.direction === "in" ? "self-end bg-[#0b7a75] text-white" : "self-start bg-white"}`}
                >
                  {m.kind === "missed_call" ? <i>Missed call</i> : m.body}
                  <span className={`mt-1 block text-[10px] ${m.direction === "in" ? "text-[#c9ece6]" : "text-[#56636b]"}`}>{hhmm(m.created_at)}</span>
                </div>
              ))}
            </div>
            <form
              className="mt-3 flex gap-2"
              onSubmit={(e) => {
                e.preventDefault();
                void send(text);
              }}
            >
              <input
                value={text}
                onChange={(e) => setText(e.target.value)}
                placeholder="Type a text…"
                aria-label="Text message"
                className="min-w-0 flex-1 rounded-xl bg-white px-3 py-2 text-[#0b2a3c] outline-none focus:ring-2 focus:ring-[#9fe5cf]"
              />
              <button disabled={busy || !text.trim()} className="rounded-xl bg-[#0b7a75] px-4 font-black uppercase disabled:opacity-50">
                Send
              </button>
            </form>
            <div className="mt-2 flex flex-wrap gap-2">
              {PRESETS.map((p) => (
                <button key={p} disabled={busy} onClick={() => void send(p)} className="rounded-lg bg-white/10 px-3 py-1.5 text-sm font-bold hover:bg-white/20">
                  {p}
                </button>
              ))}
              <button disabled={busy} onClick={() => void missedCall()} className="rounded-lg bg-[#9fe5cf] px-3 py-1.5 text-sm font-black text-[#0b2a3c]">
                Missed call
              </button>
            </div>
            {note && <p className="mt-3 px-2 text-sm text-[#f2b01e]">{note}</p>}
          </section>
        )}

        <div className="grid gap-6">
          <section className="rounded-[24px] bg-white ring-1 ring-[var(--edge)]" aria-labelledby="log-title">
            <header className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--edge)] px-6 py-4">
              <h2 id="log-title" className="text-lg font-black uppercase tracking-[-0.02em]">SMS log</h2>
              <button
                disabled={busy}
                onClick={() => void reminders()}
                className="rounded-xl bg-[#0b2a3c] px-4 py-2 text-sm font-black uppercase text-white disabled:opacity-50"
              >
                Send follow-up reminders
              </button>
            </header>
            {!sms.data ? (
              <p className="px-6 py-6 text-[var(--muted)]">{sms.error ? `Could not load: ${sms.error}` : "Loading…"}</p>
            ) : all.length === 0 ? (
              <p className="px-6 py-6 text-[var(--muted)]">No texts yet today.</p>
            ) : (
              <ul className="max-h-[560px] divide-y divide-[var(--edge)] overflow-y-auto">
                <AnimatePresence initial={false}>
                  {all.map((m) => (
                    <motion.li
                      key={m.id}
                      layout
                      initial={{ opacity: 0, y: -8 }}
                      animate={{ opacity: 1, y: 0 }}
                      className="grid grid-cols-[56px_1fr] gap-3 px-6 py-3 sm:grid-cols-[56px_120px_1fr]"
                    >
                      <span className="text-sm tabular-nums text-[var(--muted)]">{hhmm(m.created_at)}</span>
                      <span className="text-sm">
                        <b className={m.direction === "in" ? "text-[#0b7a75]" : "text-[#0b2a3c]"}>{m.direction === "in" ? "← In" : "→ Out"}</b>{" "}
                        <span className="tabular-nums text-[var(--muted)]">{masked(m.phone)}</span>
                        <span className="block text-xs font-bold text-[var(--muted)]">
                          {KIND[m.kind] ?? m.kind}
                          {m.token ? ` · ${m.token}` : ""}
                        </span>
                      </span>
                      <span className="col-span-2 text-sm sm:col-span-1">{m.kind === "missed_call" ? <i>Missed call</i> : m.body}</span>
                    </motion.li>
                  ))}
                </AnimatePresence>
              </ul>
            )}
          </section>

          <section className="rounded-[24px] bg-white ring-1 ring-[var(--edge)]" aria-labelledby="chat-title">
            <header className="border-b border-[var(--edge)] px-6 py-4">
              <h2 id="chat-title" className="text-lg font-black uppercase tracking-[-0.02em]">Help chat: what patients asked</h2>
              <p className="mt-1 text-sm text-[var(--muted)]">
                The chat answers only approved content, live hospital facts and the way to places. Questions it sent to the help desk show
                what to add next.
              </p>
            </header>
            {!chat.data || chat.data.length === 0 ? (
              <p className="px-6 py-6 text-[var(--muted)]">{chat.error ? `Could not load: ${chat.error}` : "No questions yet."}</p>
            ) : (
              <ul className="max-h-[420px] divide-y divide-[var(--edge)] overflow-y-auto">
                {chat.data.map((q, i) => {
                  const o = OUTCOME[q.outcome] ?? OUTCOME.help_desk;
                  return (
                    <li key={`${q.created_at}-${i}`} className="flex flex-wrap items-center gap-x-4 gap-y-1 px-6 py-3">
                      <span className="w-12 text-sm tabular-nums text-[var(--muted)]">{hhmm(q.created_at)}</span>
                      <span className="min-w-0 flex-1 text-sm" lang={q.language} dir={q.language === "ur" || q.language === "ks" ? "rtl" : undefined}>
                        {q.question}
                      </span>
                      <span className="rounded-full px-3 py-1 text-xs font-black" style={{ background: o.bg, color: o.ink }}>
                        {o.word}
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
