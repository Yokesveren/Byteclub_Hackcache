"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

export const PREFILL_KEY = "sevasetu.counter-prefill";

/** The counter's first screen, already open. The typed name is carried into the real counter. */
export function RegisterDialog() {
  const router = useRouter();
  const [name, setName] = useState("Kamla");
  const [who, setWho] = useState<"self" | "other">("other");

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    try {
      sessionStorage.setItem(PREFILL_KEY, JSON.stringify({ firstName: name.trim(), assisted: who === "other" }));
    } catch {
      /* storage blocked: the counter simply opens empty */
    }
    router.push("/login?next=/counter");
  };

  return (
    <form className="ss-dialog" data-ui onSubmit={submit} aria-labelledby="dlg-title">
      <h3 id="dlg-title">Register a patient</h3>
      <p>Registration counter · Chinar District Hospital</p>
      <label>
        First name
        <input type="text" value={name} onChange={(e) => setName(e.target.value)} maxLength={60} required />
      </label>
      <div style={{ marginTop: 16 }}>
        <span style={{ fontWeight: 700, fontSize: 14 }}>Who is this for?</span>
        <div className="ss-seg" role="group" aria-label="Who is this for?">
          <button type="button" aria-pressed={who === "self"} onClick={() => setWho("self")}>
            Me
          </button>
          <button type="button" aria-pressed={who === "other"} onClick={() => setWho("other")}>
            Someone else
          </button>
        </div>
      </div>
      <button className="ss-btn" type="submit">
        Make {name.trim() ? `${name.trim()}’s` : "the"} token
      </button>
      <p className="ss-dialog-note">Opens the real counter. Sign in with the counter demo account.</p>
    </form>
  );
}
