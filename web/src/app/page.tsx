import Link from "next/link";
import { Marquee, MotionRoot, ScrollProgress, SmoothScroll } from "@/components/landing/fx";
import { HeroFx } from "@/components/landing/HeroFx";
import { LiveBoardFx } from "@/components/landing/LiveBoardFx";
import { RegisterDialog } from "@/components/landing/RegisterDialog";
import { RouteFx } from "@/components/landing/RouteFx";
import { ChapterFx, CtaFx, FaqFx, IntroFx } from "@/components/landing/SectionsFx";
import { Spines } from "@/components/landing/Spines";
import { Stages } from "@/components/landing/Stages";
import "./landing.css";

const CHAPTERS = [
  {
    id: "road",
    title: "Three hours to the gate",
    text: "Before Rafiq leaves his village, the hospital card tells him the doctor is in and the X-ray is down, and opens Google Maps to the gate. He arrives knowing where to go.",
    link: { label: "See today’s board", href: "/board/H01" },
    photo: { src: "/photos/smhs-arrivals.jpg", w: 1280, h: 960, alt: "Patients and families arriving at SMHS Hospital, Srinagar, on a winter morning" },
    field: "#0b2a3c",
  },
  {
    id: "offline",
    title: "No signal, still a token",
    text: "The slip, floor plans, routes and Emergency screen live on the phone. A token made offline says “waiting to be confirmed” until it syncs.",
    link: { label: "Try the slip", href: "#flow" },
    photo: { src: "/photos/smh-inside-3.jpg", w: 1280, h: 1707, alt: "A long government hospital corridor with a direction sign and a guide line on the floor" },
    field: "#164055",
  },
  {
    id: "home",
    title: "Back home, still cared for",
    text: "The doctor sets a follow-up. Two days before it, a reminder arrives by SMS in her own language: where, when and what to bring.",
    link: { label: "Open the dashboard", href: "/login" },
    photo: { src: "/photos/bp-check.jpg", w: 1280, h: 851, alt: "A doctor checks a man's blood pressure at a free health check-up camp" },
    field: "#0d4a46",
  },
];

const QUESTIONS = [
  {
    q: "Does the app decide who is an emergency?",
    short: "No. Staff do.", link: { label: "See the triage view", href: "/facility" },
    a: "No. It only looks for warning signs and sends the person to Emergency. Qualified staff decide every medical priority, and can change the app’s suggestion up or down.",
  },
  {
    q: "What happens with no internet?",
    short: "It keeps working.", link: { label: "Walk through the app", href: "#flow" },
    a: "Registration, the symptom check, the slip, floor plans, routes and the Emergency screen all work offline. Offline tokens are marked “waiting to be confirmed”, queue positions show when they were last updated, and SMS and a printed QR slip cover phones with no data.",
  },
  {
    q: "How does indoor navigation work without GPS or beacons?",
    short: "Printed stickers.", link: { label: "Watch a route", href: "#route" },
    a: "Printed QR stickers at every junction and door are waypoints. The hospital is a graph; Dijkstra’s algorithm finds the shortest route, without stairs when needed, and a scan off the route re-plans it.",
  },
  {
    q: "Why not just use ABHA Scan and Share?",
    short: "We build on it.", link: { label: "See the gap", href: "#intro" },
    a: "We build on it: ABHA removes typing and anchors the record. What it leaves open is language, offline use, finding the room, fair queuing and follow-up, and that is what SevaSetu does.",
  },
  {
    q: "How is this fair to routine patients?",
    short: "A guard, and a log.", link: { label: "Read the log", href: "/log" },
    a: "Bands are advisory and staff-confirmed. A fairness guard tells the supervisor when a Routine patient has waited too long or been overtaken too often, and every change carries a reason the patient can read.",
  },
  {
    q: "What about people without a smartphone?",
    short: "Paper works too.", link: { label: "Open the counter", href: "/counter" },
    a: "The registration counter prints a paper slip with a QR that every scanner reads, the waiting-room board calls tokens visually, and a missed call or an SMS returns the token and its place in the queue.",
  },
];

const HOSPITALS = [
  { id: "H01", name: "Chinar District Hospital", place: "Anantnag" },
  { id: "H02", name: "Jhelum Sub-District Hospital", place: "Bijbehara" },
  { id: "H03", name: "Lidder Community Health Centre", place: "Seer, Pahalgam Road" },
];

export default function Landing() {
  return (
    <MotionRoot>
      <div className="ss" id="top">
        <SmoothScroll />
        <ScrollProgress />
        <HeroFx />

        <main>
          <Marquee
            className="ss-marquee-langs"
            items={[
              { text: "کٲشُر", lang: "ks", dir: "rtl" },
              { text: "Kashmiri" },
              { text: "اُردُو", lang: "ur", dir: "rtl" },
              { text: "Urdu" },
              { text: "डोगरी", lang: "doi" },
              { text: "Dogri" },
              { text: "हिन्दी", lang: "hi" },
              { text: "Hindi" },
              { text: "English" },
              { text: "Emergency 112 · 108" },
            ]}
          />

          <IntroFx />

          <Stages />

          <RouteFx />

          {CHAPTERS.map((c, i) => (
            <ChapterFx key={c.id} c={c} first={i === 0} />
          ))}

          <LiveBoardFx />

          <Spines />

          <FaqFx questions={QUESTIONS} />

          <CtaFx>
            <RegisterDialog />
          </CtaFx>
        </main>

      <footer className="ss-footer">
        <span className="ss-footer-name" aria-hidden>
          SevaSetu
        </span>
        <div className="ss-footer-row">
          <ul className="ss-places" aria-label="Waiting-room boards of the demo hospitals">
            {HOSPITALS.map((h) => (
              <li key={h.id}>
                <a href={`/board/${h.id}`}>{h.name}</a>
                <small>{h.place} · live waiting-room board</small>
              </li>
            ))}
          </ul>
          <nav className="ss-footer-links" aria-label="Footer">
            <div>
              <h3>Hospital staff</h3>
              <ul>
                <li><Link href="/facility">Facility view</Link></li>
                <li><Link href="/counter">Registration counter</Link></li>
                <li><Link href="/log">Transparency log</Link></li>
              </ul>
            </div>
            <div>
              <h3>Demo</h3>
              <ul>
                <li><Link href="/login">Sign in</Link></li>
                <li><Link href="/demo">Demo controls</Link></li>
                <li><Link href="/board/H01">Board, Chinar</Link></li>
              </ul>
            </div>
            <div>
              <h3>Emergency</h3>
              <ul>
                <li><a href="tel:112">Call 112</a></li>
                <li><a href="tel:108">Ambulance 108</a></li>
              </ul>
            </div>
          </nav>
        </div>
        <div className="ss-footer-legal">
          <p>
            Every hospital, doctor and patient on this site is made up for the demo. Pictures:{" "}
            <a href="https://commons.wikimedia.org/w/index.php?curid=189219724">SMHS Srinagar</a> by Salim Bin Yousuf,{" "}
            <a href="https://creativecommons.org/licenses/by/4.0">CC BY 4.0</a>;{" "}
            <a href="https://commons.wikimedia.org/w/index.php?curid=158526580">SMHS Hospital parking area</a> by Owais Khursheed, CC0;{" "}
            <a href="https://commons.wikimedia.org/wiki/File:SMH_Inside-3.jpg">Hospital corridor</a> by Nkb 21, CC0;{" "}
            <a href="https://commons.wikimedia.org/wiki/File:Blood_Pressure_Check_-_Howrah_-_2015-04-12_7660.JPG">Blood pressure check</a> by Biswarup Ganguly,{" "}
            <a href="https://creativecommons.org/licenses/by/3.0">CC BY 3.0</a>;{" "}
            <a href="https://commons.wikimedia.org/wiki/File:Kashmir_Mission_Hospital.jpg">The Kashmir Mission Hospital</a> (1914) by Ernest Neve, public domain; via Wikimedia Commons.
          </p>
          <a href="#top" className="ss-link">Back to top</a>
        </div>
      </footer>
      </div>
    </MotionRoot>
  );
}
