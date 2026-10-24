"use client";

import { AnimatePresence, motion } from "motion/react";
import { useEffect, useRef, useState } from "react";
import { scrollToY } from "./fx";

// The ten safety principles of the PRD (section 4), each with where it is actually enforced.
const RULES = [
  { title: "Emergency first, always", text: "An Emergency button on every screen, even before registration, with the route to Casualty and 112 and 108.", where: "Every screen", proof: "Bundled in the app, works offline; 112 and 108 on every board", href: "/board/H01" },
  { title: "Software never decides an emergency", text: "One warning sign stops the form: “Go to Emergency now. You don’t need a token.” No token, no wait.", where: "Symptom check", proof: "register_visit() refuses a red band: emergencies are never queued", href: "/facility" },
  { title: "Priority is advisory until staff confirm", text: "The app suggests Routine or Priority. A nurse confirms or changes it, in either direction.", where: "Facility view", proof: "staff_action('set_band') writes confirmed_band; the app can’t lower it", href: "/facility" },
  { title: "Never tell anyone they can wait", text: "A time window is an estimate of the consultation, never a statement about safety.", where: "Every queue screen", proof: "Always a range with “last updated”, never a promise", href: "/board/H01" },
  { title: "Urgent flags outrank everything", text: "“I need urgent help” sits on top of the staff screen, with sound, until someone taps Seen.", where: "Facility view", proof: "urgent_flags → banner with alarm until ack_urgent_flag()", href: "/facility" },
  { title: "Flagged patients are never moved away", text: "Wait Anywhere and remote tokens switch off for anyone with a flag or a staff-set Priority.", where: "Queue rules", proof: "wait_anywhere_allowed is false for flagged or Priority visits", href: "/facility" },
  { title: "The chatbot follows protocol", text: "It answers where, when and what to bring. It never diagnoses and never gives a dose.", where: "Help chat", proof: "Approved FAQ content only; a warning sign hands off to Emergency", href: "#questions" },
  { title: "Honest about being offline", text: "“Last updated at 10:42.” Offline tokens say “waiting to be confirmed” until they sync.", where: "Slip and My Turn", proof: "offline_token is re-issued on sync, with the reason shown", href: "#flow" },
  { title: "Privacy by design", text: "The slip and the board show token, first name and department. Movement is anonymous.", where: "Slip and board", proof: "get_board() returns token and first name only", href: "/board/H01" },
  { title: "Every queue decision is logged", text: "Who changed what and why, and the patient sees a short reason when their place moves.", where: "Transparency log", proof: "queue_events is append-only: a trigger blocks every edit", href: "/log" },
];

// The open card takes the brand's colours in turn.
const CARD = [
  { bg: "#0b2a3c", ink: "#f2f2f2", soft: "#b4b4b4", mark: "#9fe5cf" },
  { bg: "#0b7a75", ink: "#ffffff", soft: "#c9ece6", mark: "#9fe5cf" },
  { bg: "#14324a", ink: "#f2f2f2", soft: "#b9d3e3", mark: "#dcecf7" },
];
const GAP = 8;
const UPCOMING = 40;

export function Spines() {
  const wrap = useRef<HTMLElement>(null);
  const deck = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(0);
  const [deckW, setDeckW] = useState(1200);
  const [stacked, setStacked] = useState(false);

  useEffect(() => {
    const onScroll = () => {
      const el = wrap.current;
      if (!el) return;
      const r = el.getBoundingClientRect();
      const total = el.offsetHeight - window.innerHeight;
      const p = total > 0 ? Math.min(1, Math.max(0, -r.top / total)) : 0;
      setActive(Math.min(RULES.length - 1, Math.floor(p * RULES.length)));
    };
    const onResize = () => {
      setStacked(window.matchMedia("(max-width: 900px)").matches);
      if (deck.current) setDeckW(deck.current.clientWidth);
      onScroll();
    };
    onResize();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onResize);
    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onResize);
    };
  }, []);

  // Read rules fold into spines on the left, the open card fills the middle, the rest wait as stubs on the right.
  const spine = Math.max(52, Math.min(92, (deckW * 0.55) / (RULES.length - 1) - GAP));
  const upcoming = RULES.length - 1 - active;
  const cardW = deckW - active * (spine + GAP) - upcoming * (UPCOMING + GAP);
  const leftOf = (i: number) =>
    i < active ? i * (spine + GAP) : i === active ? active * (spine + GAP) : active * (spine + GAP) + cardW + GAP + (i - active - 1) * (UPCOMING + GAP);
  const widthOf = (i: number) => (i < active ? spine : i === active ? cardW : UPCOMING);

  const jump = (i: number) => {
    const el = wrap.current;
    if (!el) return;
    const total = el.offsetHeight - window.innerHeight;
    scrollToY(el.offsetTop + ((i + 0.5) / RULES.length) * total);
  };

  return (
    <section ref={wrap} className="ss-spines" aria-labelledby="rules-title">
      <div className="ss-spines-stage">
        <motion.h2
          id="rules-title"
          className="ss-display"
          initial={{ opacity: 0, y: 50 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ type: "spring", stiffness: 100, damping: 18 }}
        >
          Ten rules it never breaks
        </motion.h2>
        <p className="ss-spines-meta" aria-live="polite">
          <span>
            Rule {active + 1} of {RULES.length}
          </span>
          <span className="ss-spines-dots" aria-hidden>
            {RULES.map((_, i) => (
              <motion.span key={i} animate={{ width: i === active ? 28 : 8, opacity: i <= active ? 1 : 0.3 }} transition={{ type: "spring", stiffness: 300, damping: 26 }} />
            ))}
          </span>
          <span>{active < RULES.length - 1 ? "Keep scrolling to open the next rule, or tap any spine." : "All ten. Tap any spine to reopen it."}</span>
        </p>

        <div ref={deck} className="ss-deck">
          {RULES.map((r, i) => {
            const state = stacked ? "open" : i < active ? "spine" : i === active ? "open" : "next";
            const tone = CARD[i % CARD.length];
            return (
              <motion.article
                key={r.title}
                className={`ss-book ${state}`}
                style={state === "open" ? { background: tone.bg, color: tone.ink } : undefined}
                initial={false}
                animate={stacked ? {} : { left: leftOf(i), width: widthOf(i) }}
                transition={{ type: "spring", stiffness: 170, damping: 26 }}
                onClick={state !== "open" ? () => jump(i) : undefined}
                aria-hidden={state === "next" || undefined}
              >
                {state === "spine" && (
                  <>
                    <span className="ss-book-title">{r.title}</span>
                    <span className="ss-book-num">{i + 1}</span>
                  </>
                )}
                {state === "next" && <span className="ss-book-num">{i + 1}</span>}
                {state === "open" && (
                  <AnimatePresence mode="wait">
                    <motion.div
                      key={r.title}
                      className="ss-book-open"
                      initial={{ opacity: 0, y: 24 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ type: "spring", stiffness: 200, damping: 24, delay: 0.1 }}
                    >
                      <span className="ss-book-watermark" style={{ color: tone.mark }} aria-hidden>
                        {i + 1}
                      </span>
                      <div className="ss-book-top">
                        <span style={{ color: tone.mark }}>Rule {i + 1}</span>
                        <span className="ss-book-where">{r.where}</span>
                      </div>
                      <h3>{r.title}</h3>
                      <p className="ss-book-text">{r.text}</p>
                      <div className="ss-book-foot">
                        <p className="ss-book-proof" style={{ color: tone.soft }}>
                          <strong style={{ color: tone.ink }}>Enforced by</strong> {r.proof}
                        </p>
                        <a className="ss-book-cta" href={r.href} style={{ background: tone.mark }}>
                          See it →
                        </a>
                      </div>
                    </motion.div>
                  </AnimatePresence>
                )}
              </motion.article>
            );
          })}
        </div>
        <p className="ss-spines-foot">
          Ten principles, each one enforced by the queue rules in the database, not by good intentions.{" "}
          <a className="ss-link" href="/log">
            Read the transparency log
          </a>
        </p>
      </div>
    </section>
  );
}
