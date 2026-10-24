"use client";

import { useState } from "react";
import { REASONS } from "@/lib/format";

/** Every queue change asks for a reason from a pick-list plus an optional note (S10). */
export function ReasonDialog({
  title,
  description,
  defaultReason,
  confirmLabel = "Confirm",
  danger,
  children,
  onCancel,
  onConfirm,
}: {
  title: string;
  description?: string;
  defaultReason?: string;
  confirmLabel?: string;
  danger?: boolean;
  children?: React.ReactNode;
  onCancel: () => void;
  onConfirm: (reason: string, note: string) => Promise<void>;
}) {
  const [reason, setReason] = useState(defaultReason ?? "");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reason) {
      setError("Pick a reason. The patient sees it and it goes into the transparency log.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onConfirm(reason, note);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={onCancel}>
      <form
        onSubmit={submit}
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md rounded-xl bg-white p-5 shadow-xl" /* taste-skill-ignore: dialog */
        role="dialog"
        aria-modal="true"
        aria-labelledby="reason-title"
      >
        <h2 id="reason-title" className="text-lg font-bold">
          {title}
        </h2>
        {description && <p className="mt-1 text-sm text-[var(--muted)]">{description}</p>}
        {children}
        <fieldset className="mt-4">
          <legend className="text-sm font-semibold">Reason</legend>
          <div className="mt-2 grid grid-cols-1 gap-1.5 sm:grid-cols-2">
            {REASONS.map((r) => (
              <label
                key={r.code}
                className={`flex cursor-pointer items-center gap-2 rounded border px-2 py-1.5 text-sm ${
                  reason === r.code ? "border-[var(--ink)] bg-[var(--raised)] font-semibold" : "border-[var(--edge)]"
                }`}
              >
                <input type="radio" name="reason" value={r.code} checked={reason === r.code} onChange={() => setReason(r.code)} />
                {r.label}
              </label>
            ))}
          </div>
        </fieldset>
        <label className="mt-3 block text-sm font-semibold">
          Note (optional)
          <input
            value={note}
            onChange={(e) => setNote(e.target.value)}
            maxLength={280}
            className="mt-1 w-full rounded border border-[var(--control)] px-2 py-1.5 font-normal"
          />
        </label>
        {error && <p className="mt-3 text-sm font-semibold text-red-700">{error}</p>}
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onCancel} className="rounded border border-[var(--control)] px-4 py-2">
            Cancel
          </button>
          <button
            type="submit"
            disabled={busy}
            className={`rounded px-4 py-2 font-semibold text-white disabled:opacity-60 ${
              danger ? "bg-red-700 hover:bg-red-800" : "bg-[var(--ink)] hover:bg-[#204f65]"
            }`}
          >
            {busy ? "Saving…" : confirmLabel}
          </button>
        </div>
      </form>
    </div>
  );
}
