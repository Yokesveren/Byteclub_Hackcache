import type { Metadata } from "next";
import { Chivo } from "next/font/google";
import "./globals.css";

// One face across the site and the dashboard (taste-skill sheet: Chivo, 400/700/900).
const chivo = Chivo({
  variable: "--font-chivo",
  subsets: ["latin"],
  weight: ["400", "700", "900"],
});

export const metadata: Metadata = {
  title: "SevaSetu",
  description:
    "Hospital queue and wayfinding for government hospitals in Jammu and Kashmir: register in your language, get a token, follow QR stickers to the right room, even with no signal.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${chivo.variable} h-full antialiased`}>
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
