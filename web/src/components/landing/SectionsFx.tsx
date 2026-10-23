"use client";

import { AnimatePresence, motion, useScroll, useTransform } from "motion/react";
import Image from "next/image";
import { useRef, useState } from "react";
import { CountUp, Parallax, Rise, Stagger } from "./fx";

export interface Chapter {
  id: string;
  title: string;
  text: string;
  link: { label: string; href: string };
  photo: { src: string; w: number; h: number; alt: string };
  field: string;
}

/** One chapter per screen: the field closes in to full bleed, the portrait drifts against the scroll. */
export function ChapterFx({ c, first }: { c: Chapter; first: boolean }) {
  const ref = useRef<HTMLElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start end", "start start"] });
  const inset = useTransform(scrollYProgress, [0, 1], [40, 0]);
  const radius = useTransform(scrollYProgress, [0, 1], [28, 0]);
  const Heading = first ? motion.h2 : motion.h3;

  return (
    <section ref={ref} className="ss-chapter" aria-labelledby={`ch-${c.id}`} style={{ ["--field" as string]: c.field }}>
      <motion.div className="ss-chapter-field" style={{ inset, borderRadius: radius }} />
      <figure>
        <Parallax distance={60}>
          <Image src={c.photo.src} width={c.photo.w} height={c.photo.h} sizes="(max-width: 720px) 80vw, 506px" alt={c.photo.alt} />
        </Parallax>
      </figure>
      <Heading
        id={`ch-${c.id}`}
        className="ss-chapter-title"
        initial={{ opacity: 0, x: -40 }}
        whileInView={{ opacity: 1, x: 0 }}
        viewport={{ once: true, amount: 0.15 }}
        transition={{ type: "spring", stiffness: 120, damping: 20 }}
      >
        {c.title}
      </Heading>
      <motion.p
        className="ss-chapter-text"
        initial={{ opacity: 0, y: 30 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, amount: 0.15 }}
        transition={{ type: "spring", stiffness: 120, damping: 20, delay: 0.15 }}
      >
        {c.text}
      </motion.p>
      <motion.a
        className="ss-link ss-chapter-link"
        href={c.link.href}
        initial={{ opacity: 0 }}
        whileInView={{ opacity: 1 }}
        viewport={{ once: true }}
        transition={{ delay: 0.3 }}
        whileHover={{ x: 6 }}
      >
        {c.link.label} →
      </motion.a>
    </section>
  );
}

/** "205 of 3,000", counted up, beside the Dal Lake clip and the Banihal road. */
export function IntroFx() {
  return (
    <section id="intro" className="ss-intro ss-grid" aria-labelledby="intro-title">
      <motion.h2
        id="intro-title"
        className="ss-display"
        initial={{ opacity: 0, y: 40 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, amount: 0.15 }}
        transition={{ type: "spring", stiffness: 100, damping: 18 }}
      >
        <CountUp to={205} /> of <CountUp to={3000} duration={2.2} />
      </motion.h2>
      <motion.p
        className="ss-intro-text"
        initial={{ opacity: 0, y: 30 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, amount: 0.15 }}
        transition={{ type: "spring", stiffness: 100, damping: 18, delay: 0.15 }}
      >
        One day at SMHS Srinagar, 205 patients registered digitally. Over 3,000 came.
        <small>
          The rest queued on paper, in English. Figures as reported by Greater Kashmir: the digital rails exist, and most
          patients still cannot use them.
        </small>
      </motion.p>
      <motion.figure
        className="ss-intro-clip"
        initial={{ opacity: 0, rotate: 8, y: 80 }}
        whileInView={{ opacity: 1, rotate: 3, y: 0 }}
        viewport={{ once: true, amount: 0.15 }}
        transition={{ type: "spring", stiffness: 70, damping: 16 }}
        whileHover={{ rotate: 0, scale: 1.02 }}
      >
        <video
          src="/photos/dal-lake.webm"
          poster="/photos/dal-lake-poster.jpg"
          width={608}
          height={1080}
          muted
          loop
          autoPlay
          playsInline
          preload="metadata"
          aria-label="Shikaras crossing Dal Lake, Srinagar, at dusk"
        />
        <figcaption>Dal Lake, Srinagar, at dusk.</figcaption>
      </motion.figure>
      <motion.figure
        className="ss-intro-chip"
        initial={{ opacity: 0, rotate: -12, x: -60 }}
        whileInView={{ opacity: 1, rotate: -4, x: 0 }}
        viewport={{ once: true, amount: 0.15 }}
        transition={{ type: "spring", stiffness: 70, damping: 16, delay: 0.2 }}
        whileHover={{ rotate: 0, scale: 1.04 }}
      >
        <Image
          src="/photos/banihal-road.jpg"
          width={1600}
          height={1197}
          sizes="(max-width: 720px) 40vw, 16vw"
          alt="Snow along the Banihal–Qazigund road by the tunnel, with the Pir Panjal range behind"
        />
        <figcaption>The Banihal–Qazigund road, the way into the valley.</figcaption>
      </motion.figure>
    </section>
  );
}

export interface Question {
  q: string;
  short: string;
  a: string;
  link: { label: string; href: string };
}

/**
 * Fair questions: pick a question on the left, the answer opens on the right as a card that leads
 * with a one-line verdict. On a phone it folds into an accordion.
 */
export function FaqFx({ questions }: { questions: Question[] }) {
  const [open, setOpen] = useState(0);
  const q = questions[open];

  return (
    <section className="ss-faq" aria-labelledby="questions">
      <div className="ss-faq-top">
        <motion.h2
          id="questions"
          className="ss-display"
          initial={{ opacity: 0, y: 60 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.3 }}
          transition={{ type: "spring", stiffness: 90, damping: 16 }}
        >
          Fair questions
        </motion.h2>
        <p>The six things judges, doctors and district officers ask first, answered straight.</p>
      </div>

      <div className="ss-faq-grid">
        <div className="ss-faq-list" role="tablist" aria-label="Questions">
          {questions.map((x, i) => {
            const active = i === open;
            return (
              <div key={x.q} className="ss-faq-item">
                <button
                  type="button"
                  role="tab"
                  id={`faq-tab-${i}`}
                  aria-selected={active}
                  aria-controls={`faq-panel-${i}`}
                  onClick={() => setOpen(i)}
                  className={`ss-faq-q ${active ? "on" : ""}`}
                >
                  {active && (
                    <motion.span
                      layoutId="faq-pill"
                      className="ss-faq-pill"
                      transition={{ type: "spring", stiffness: 380, damping: 32 }}
                    />
                  )}
                  <span className="ss-faq-n">Q{i + 1}</span>
                  <span className="ss-faq-qtext">{x.q}</span>
                  <motion.span className="ss-faq-chev" animate={{ rotate: active ? 90 : 0 }} aria-hidden>
                    →
                  </motion.span>
                </button>
                {/* phone: the answer opens in place */}
                <AnimatePresence initial={false}>
                  {active && (
                    <motion.div
                      className="ss-faq-inline"
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: "auto", opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      transition={{ type: "spring", stiffness: 300, damping: 32 }}
                    >
                      <AnswerCard q={x} n={i + 1} />
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            );
          })}
        </div>

        {/* desktop: one big answer card */}
        <div className="ss-faq-panel" role="tabpanel" id={`faq-panel-${open}`} aria-labelledby={`faq-tab-${open}`}>
          <AnimatePresence mode="wait">
            <motion.div
              key={open}
              initial={{ opacity: 0, y: 30, rotateX: -8 }}
              animate={{ opacity: 1, y: 0, rotateX: 0 }}
              exit={{ opacity: 0, y: -20 }}
              transition={{ type: "spring", stiffness: 260, damping: 26 }}
              style={{ transformPerspective: 1200 }}
            >
              <AnswerCard q={q} n={open + 1} big />
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </section>
  );
}

function AnswerCard({ q, n, big }: { q: Question; n: number; big?: boolean }) {
  return (
    <article className={`ss-faq-card ${big ? "big" : ""}`}>
      <span className="ss-faq-mark" aria-hidden>
        Q{n}
      </span>
      <p className="ss-faq-short">{q.short}</p>
      <p className="ss-faq-a">{q.a}</p>
      <a className="ss-faq-link" href={q.link.href}>
        {q.link.label} →
      </a>
    </article>
  );
}

/** The closing words rise; the counter's dialog lifts in on a spring. */
export function CtaFx({ children }: { children: React.ReactNode }) {
  return (
    <section className="ss-cta ss-grid" aria-labelledby="cta-title">
      <Stagger className="ss-cta-copy">
        <Rise as="h2">
          <span id="cta-title" className="ss-display">
            Make a token
          </span>
        </Rise>
        <Rise as="p">The counter opens with her name filled in. Register her, and the slip comes out with a QR, a room and a time window.</Rise>
      </Stagger>
      <motion.div
        className="ss-cta-dialog"
        initial={{ opacity: 0, y: 80, rotateX: 20, scale: 0.92 }}
        whileInView={{ opacity: 1, y: 0, rotateX: 0, scale: 1 }}
        viewport={{ once: true, amount: 0.15 }}
        transition={{ type: "spring", stiffness: 90, damping: 16 }}
        style={{ transformPerspective: 1000 }}
      >
        {children}
      </motion.div>
    </section>
  );
}
