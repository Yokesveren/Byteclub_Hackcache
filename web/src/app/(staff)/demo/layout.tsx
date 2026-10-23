import type { Metadata } from "next";

export const metadata: Metadata = { title: "Demo controls · SevaSetu" };

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
