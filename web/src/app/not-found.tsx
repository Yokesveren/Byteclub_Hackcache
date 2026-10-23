import Link from "next/link";

export default function NotFound() {
  return (
    <main className="grid min-h-screen bg-[#0a0a0a] text-[#f2f2f2] lg:grid-cols-2">
      <section className="flex flex-col justify-between p-6 sm:p-10">
        <Link href="/" className="flex items-center gap-3">
          <svg viewBox="0 0 30 30" width="32" height="32" aria-hidden>
            <rect width="30" height="30" rx="6" fill="#b0298f" />
            <path d="M5 21 Q15 5 25 21" fill="none" stroke="#ffffff" strokeWidth="3.4" />
            <rect x="4" y="21" width="22" height="3.4" fill="#ffffff" />
          </svg>
          <span className="text-2xl font-black uppercase tracking-[-0.04em]">SevaSetu</span>
        </Link>
        <div className="my-16">
          <p className="text-lg font-bold text-[#ffc013]">Wrong turn</p>
          <h1 className="mt-3 text-[clamp(3.5rem,2rem+6vw,7rem)] font-black uppercase leading-[0.86] tracking-[-0.05em]">
            No sticker
            <br />
            here
          </h1>
          <p className="mt-6 max-w-[36ch] text-lg text-[#d6d6d6]">
            This page does not exist. No problem: let’s take a new route.
          </p>
        </div>
        <p className="text-sm text-[#9a9a9a]">
          Emergency? Call <a href="tel:112" className="font-bold text-white underline">112</a> · Ambulance{" "}
          <a href="tel:108" className="font-bold text-white underline">108</a>
        </p>
      </section>
      <section className="flex flex-col justify-center gap-3 bg-[#cbe4f2] p-6 text-[#0a0a0a] sm:p-10 lg:rounded-l-[32px]">
        {[
          { href: "/", label: "Home", note: "What SevaSetu is", bg: "#ffc013" },
          { href: "/login", label: "Staff sign-in", note: "The hospital dashboard", bg: "#ffffff" },
          { href: "/board/H01", label: "Waiting-room board", note: "Chinar District Hospital, live", bg: "#f1e3b5" },
        ].map((l) => (
          <Link
            key={l.href}
            href={l.href}
            className="flex items-center justify-between rounded-2xl p-6 transition-transform hover:translate-x-1"
            style={{ background: l.bg }}
          >
            <span>
              <span className="block text-2xl font-black uppercase tracking-[-0.03em]">{l.label}</span>
              <span className="text-sm font-bold opacity-75">{l.note}</span>
            </span>
            <span className="text-2xl font-black" aria-hidden>
              →
            </span>
          </Link>
        ))}
      </section>
    </main>
  );
}
