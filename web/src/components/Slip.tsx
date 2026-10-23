"use client";

import QRCode from "qrcode";
import { useEffect, useState } from "react";
import { BandChip } from "@/components/BandChip";
import { floorName, hhmm } from "@/lib/format";
import type { MyTurn } from "@/lib/types";

/**
 * OPD slip (PRD 8.5). Shows token, first name and department only (S9).
 * The QR holds the hospital code and visit id, never health data.
 */
export function Slip({ slip }: { slip: MyTurn }) {
  const [qr, setQr] = useState<string | null>(null);

  useEffect(() => {
    QRCode.toDataURL(slip.slip_qr, { margin: 1, width: 220, errorCorrectionLevel: "M" })
      .then(setQr)
      .catch(() => setQr(null));
  }, [slip.slip_qr]);

  const e = slip.estimate;
  const mapsUrl = `https://www.google.com/maps/dir/?api=1&destination=${slip.hospital.lat},${slip.hospital.lng}`;

  return (
    <article className="print-slip rounded-xl border-2 border-[var(--ink)] bg-white p-5 text-[var(--ink)]">
      <header className="flex items-start justify-between gap-3 border-b border-dashed border-[var(--control)] pb-3">
        <div>
          <p className="text-[13px] text-[var(--muted)]">{slip.hospital.name}</p>
          <p className="text-sm font-semibold">{slip.department.name} OPD</p>
        </div>
        <p className="text-xs text-[var(--muted)]">SevaSetu</p>
      </header>

      <div className="mt-3 flex items-start justify-between gap-3">
        <div>
          <p className="text-[13px] text-[var(--muted)]">Token</p>
          <p className="text-5xl font-black tabular-nums leading-none">{slip.token}</p>
          <p className="mt-2 text-xl font-semibold">{slip.first_name}</p>
          <div className="mt-2">
            <BandChip band={slip.band} provisional={slip.band_provisional} size="lg" />
          </div>
        </div>
        {qr && (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={qr} alt={`QR code for token ${slip.token}`} width={120} height={120} className="shrink-0" />
        )}
      </div>

      {slip.doctor && (
        <p className="mt-4 text-lg">
          <strong>{slip.doctor.room}</strong>, {floorName(slip.doctor.floor)}
          {slip.doctor.stair_free && slip.doctor.floor > 0 && <span title="Lift and ramp route available"> · lift and ramp</span>}
          <br />
          <span className="text-base text-[#2b363d]">{slip.doctor.name}</span>
        </p>
      )}

      {e && (
        <div className="mt-3 rounded-lg bg-[var(--raised)] p-3">
          <p className="text-lg">
            <strong>{e.people_ahead}</strong> ahead of you
          </p>
          {e.paused ? (
            <p className="text-base">The doctor is not seeing patients right now. Time will update when they return.</p>
          ) : (
            <p className="text-base">
              Likely between <strong>{hhmm(e.window_start)}</strong> and <strong>{hhmm(e.window_end)}</strong>
            </p>
          )}
          <p className="text-xs text-[var(--muted)]">Last updated at {hhmm(slip.last_updated)}. This is an estimate only.</p>
        </div>
      )}

      <p className="mt-3 rounded-lg border-2 border-red-700 p-2 text-sm font-semibold text-red-800">
        Feeling worse? Tell any staff member now or go to Emergency. Emergency 112 · Ambulance 108
      </p>

      <p className="mt-3 text-xs text-[var(--muted)]">
        Scan the QR in the SevaSetu app (“I have my slip”) to follow your turn and get directions.
        <span className="hidden print:inline"> {slip.hospital.address}</span>
        <a href={mapsUrl} target="_blank" rel="noreferrer" className="ml-1 underline print:hidden">
          Open hospital in Google Maps
        </a>
      </p>
    </article>
  );
}
