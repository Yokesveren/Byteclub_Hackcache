"use client";

import QRCode from "qrcode";
import { useEffect, useState } from "react";

/** A QR of the download link on this site's own address, so a phone on the same network can scan it. */
export function DownloadQr({ path }: { path: string }) {
  const [src, setSrc] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    QRCode.toDataURL(new URL(path, window.location.origin).toString(), { margin: 1, width: 240, errorCorrectionLevel: "M" })
      .then((url) => live && setSrc(url))
      .catch(() => live && setSrc(null));
    return () => {
      live = false;
    };
  }, [path]);

  if (!src) return <div className="h-[120px] w-[120px] rounded-xl bg-white/10" aria-hidden />;
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={src} alt="QR code to download the patient app" width={120} height={120} className="rounded-xl bg-white p-1" />;
}
