"use client";

import { useRef, useState } from "react";
import { rpc } from "@/lib/supabase";

interface Reply {
  type: "answer" | "emergency" | "refuse" | "help_desk";
  text: string;
  action?: { type: "place"; tag: string } | { type: "screen"; screen: string } | null;
}

const TRY = ["What should I bring?", "Where is the pharmacy?", "OPD timings", "Which tablet for fever?", "मेरी पर्ची खो गई"];
const PLACE: Record<string, string> = {
  pharmacy: "Pharmacy", lab: "Laboratory", xray: "X-ray", toilet: "Toilets", registration: "Registration counter",
  help: "Help desk", waiting: "Waiting area", lift: "Lift", emergency: "Emergency",
};

/**
 * The patient app's help chat on the web (PRD 11.4): answers only approved content, live hospital facts and places.
 * A warning sign ends the chat with the Emergency message (S7).
 */
export function HelpChat() {
  const [log, setLog] = useState<{ q: string; r: Reply | null }[]>([]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  // An anonymous id for this conversation, made on the first question (never during render).
  const session = useRef<string | null>(null);

  const ask = async (q: string) => {
    if (!q.trim() || busy) return;
    session.current ??= `web-${crypto.randomUUID()}`;
    setBusy(true);
    setText("");
    setLog((l) => [...l, { q, r: null }]);
    let r: Reply;
    try {
      r = await rpc<Reply>("chat_ask", { p: { hospital_id: "H01", lang: /[ऀ-ॿ]/.test(q) ? "hi" : /[؀-ۿ]/.test(q) ? "ur" : "en", text: q, session_id: session.current } });
    } catch {
      r = { type: "help_desk", text: "Chat is available when you are online. Please ask at the help desk." };
    }
    setLog((l) => [...l.slice(0, -1), { q, r }]);
    setBusy(false);
  };

  return (
    <section aria-labelledby="chat-title" className="rounded-2xl bg-white p-6">
      <h2 id="chat-title" className="text-2xl font-black uppercase tracking-[-0.03em]">Ask the help chat</h2>
      <p className="mt-1 text-sm font-bold opacity-75">
        Where, when, what to bring, what happens next. It never diagnoses or suggests medicines, and sends warning signs to Emergency.
      </p>
      <div className="mt-4 flex max-h-72 flex-col gap-2 overflow-y-auto" aria-live="polite">
        {log.map((m, i) => (
          <div key={i} className="flex flex-col gap-2">
            <p className="max-w-[85%] self-end rounded-2xl bg-[#0b7a75] px-3 py-2 text-sm text-white">{m.q}</p>
            {m.r ? (
              <div
                className="max-w-[90%] self-start rounded-2xl px-3 py-2 text-sm"
                style={m.r.type === "emergency" ? { background: "#c4242a", color: "#fff" } : { background: "#edf3f8", color: "#0b2a3c" }}
              >
                {m.r.type === "emergency" && <b className="block uppercase">◆ Go to Emergency now</b>}
                {m.r.text}
                {m.r.action?.type === "place" && (
                  <span className="mt-1 block text-xs font-black uppercase text-[#0b7a75]">In the app: show me the way to {PLACE[m.r.action.tag] ?? m.r.action.tag} →</span>
                )}
                {m.r.type === "emergency" && (
                  <span className="mt-1 block text-xs font-bold">
                    Call <a href="tel:112" className="underline">112</a> · Ambulance <a href="tel:108" className="underline">108</a>
                  </span>
                )}
              </div>
            ) : (
              <p className="self-start text-sm opacity-60">…</p>
            )}
          </div>
        ))}
      </div>
      {log.length === 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          {TRY.map((t) => (
            <button key={t} onClick={() => void ask(t)} className="rounded-full bg-[#edf3f8] px-3 py-1.5 text-sm font-bold hover:bg-[#dcecf7]">
              {t}
            </button>
          ))}
        </div>
      )}
      <form
        className="mt-3 flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          void ask(text);
        }}
      >
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Type a question…"
          aria-label="Your question"
          className="min-w-0 flex-1 rounded-xl bg-[#edf3f8] px-3 py-2 outline-none focus:ring-2 focus:ring-[#0b7a75]"
        />
        <button disabled={busy || !text.trim()} className="rounded-xl bg-[#0b2a3c] px-4 font-black uppercase text-white disabled:opacity-50">
          Ask
        </button>
      </form>
    </section>
  );
}
