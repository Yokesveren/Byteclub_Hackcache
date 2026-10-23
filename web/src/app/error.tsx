"use client";

import Link from "next/link";
import { useEffect } from "react";

/** Any page that throws lands here instead of a blank screen, with a way to retry. */
export default function ErrorPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main className="grid min-h-screen place-items-center bg-[#0a0a0a] p-6 text-[#f2f2f2]">
      <div className="max-w-xl">
        <p className="text-lg font-bold text-[#ffc013]">Something went wrong</p>
        <h1 className="mt-3 text-[clamp(3rem,2rem+4vw,5.5rem)] font-black uppercase leading-[0.88] tracking-[-0.05em]">
          Let’s try
          <br />
          that again
        </h1>
        <p className="mt-6 text-lg text-[#d6d6d6]">
          The screen hit an error. Nothing in the queue was changed. Retry, or go back to the start.
        </p>
        {error.digest && <p className="mt-2 text-sm text-[#9a9a9a]">Reference {error.digest}</p>}
        <div className="mt-8 flex flex-wrap gap-3">
          <button onClick={reset} className="h-12 rounded-xl bg-[#b0298f] px-6 font-black uppercase text-white hover:bg-[#951f78]">
            Retry
          </button>
          <Link href="/" className="grid h-12 place-items-center rounded-xl bg-[#262626] px-6 font-bold text-white hover:bg-[#333]">
            Home
          </Link>
        </div>
      </div>
    </main>
  );
}
