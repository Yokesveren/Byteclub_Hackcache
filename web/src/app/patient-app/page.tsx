import type { Metadata } from "next";
import Link from "next/link";
import { DownloadQr } from "./DownloadQr";
import { HelpChat } from "./HelpChat";

export const metadata: Metadata = {
  title: "Patient app · SevaSetu",
  description: "The SevaSetu patient app for Android: a token without the counter queue, your turn live, and the way to the doctor's room.",
};

const APK = "/download/sevasetu-patient.apk";

const FEATURES = [
  { title: "Register without the queue", note: "One question per screen. Warning signs first: any yes goes straight to Emergency, no token.", bg: "#9fe5cf" },
  { title: "Your turn, live", note: "People ahead, a time window and the reason whenever your place changes. An alert at 3 ahead and when called.", bg: "#ffffff" },
  { title: "Find the room", note: "Scan the QR stickers on the walls. Step-by-step directions, stair-free routes, a new route after a wrong turn.", bg: "#edf3f8" },
  { title: "Works with no signal", note: "Emergency, maps, the symptom check and your last ticket live on the phone. Offline tokens sync later.", bg: "#ffffff" },
  { title: "Five languages", note: "Kashmiri, Urdu, Dogri, Hindi and English, with Listen to read any screen aloud.", bg: "#edf3f8" },
  { title: "No data? Use SMS", note: "From any phone, text SLIP to the hospital number for your token and place, REG to register, HELP if you feel worse.", bg: "#ffffff" },
];

export default function PatientApp() {
  return (
    <main className="grid min-h-screen bg-[#0b2a3c] text-[#f2f2f2] lg:grid-cols-2">
      <section className="flex flex-col justify-between gap-10 p-6 sm:p-10">
        <Link href="/" className="flex items-center gap-3">
          <svg viewBox="0 0 30 30" width="32" height="32" aria-hidden>
            <rect width="30" height="30" rx="6" fill="#0b7a75" />
            <path d="M5 21 Q15 5 25 21" fill="none" stroke="#ffffff" strokeWidth="3.4" />
            <rect x="4" y="21" width="22" height="3.4" fill="#ffffff" />
          </svg>
          <span className="text-2xl font-black uppercase tracking-[-0.04em]">SevaSetu</span>
        </Link>

        <div>
          <p className="text-lg font-bold text-[#9fe5cf]">For patients and families</p>
          <h1 className="mt-3 text-[clamp(3.5rem,2rem+6vw,7rem)] font-black uppercase leading-[0.86] tracking-[-0.05em]">
            The patient
            <br />
            app
          </h1>
          <p className="mt-6 max-w-[40ch] text-lg text-[#d6d6d6]">
            Your OPD token on your phone, your place in the queue as it moves, and the way to the doctor&apos;s room. It talks to the
            same system the hospital staff use on this website.
          </p>

          <div className="mt-8 flex flex-wrap items-center gap-6">
            <a
              href={APK}
              download
              className="inline-flex items-center gap-3 rounded-xl bg-[#0b7a75] px-6 py-4 text-lg font-black uppercase tracking-[-0.01em] text-white transition-colors hover:bg-[#08615d]"
            >
              Download for Android
              <span aria-hidden>↓</span>
            </a>
            <DownloadQr path={APK} />
          </div>
          <p className="mt-4 max-w-[46ch] text-sm text-[#b4b4b4]">
            Demo build (Android 8 or newer). Your phone will ask to allow installs from this browser. Scan the code with a phone on the
            same Wi-Fi to download it there.
          </p>
        </div>

        <p className="text-sm text-[#9a9a9a]">
          Emergency? Call <a href="tel:112" className="font-bold text-white underline">112</a> · Ambulance{" "}
          <a href="tel:108" className="font-bold text-white underline">108</a>
        </p>
      </section>

      <section className="flex flex-col justify-center gap-3 bg-[#dcecf7] p-6 text-[#0b2a3c] sm:p-10 lg:rounded-l-[32px]">
        {FEATURES.map((f) => (
          <div key={f.title} className="rounded-2xl p-6" style={{ background: f.bg }}>
            <span className="block text-2xl font-black uppercase tracking-[-0.03em]">{f.title}</span>
            <span className="mt-1 block text-base font-bold opacity-80">{f.note}</span>
          </div>
        ))}
        <HelpChat />
        <Link
          href="/board/H01"
          className="mt-3 flex items-center justify-between rounded-2xl bg-[#0b2a3c] p-6 text-white transition-transform hover:translate-x-1"
        >
          <span>
            <span className="block text-2xl font-black uppercase tracking-[-0.03em]">See a live queue</span>
            <span className="text-sm font-bold text-[#9fe5cf]">The waiting-room board at Chinar District Hospital</span>
          </span>
          <span className="text-2xl font-black" aria-hidden>
            →
          </span>
        </Link>
      </section>
    </main>
  );
}
