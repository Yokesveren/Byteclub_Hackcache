import { notFound } from "next/navigation";
import { Suspense } from "react";
import { BoardScreen } from "./BoardScreen";

const HOSPITALS = ["H01", "H02", "H03"];

const NAMES: Record<string, string> = { H01: "Chinar District Hospital", H02: "Jhelum Sub-District Hospital", H03: "Lidder Community Health Centre" };

export async function generateMetadata({ params }: PageProps<"/board/[hospitalId]">) {
  const { hospitalId } = await params;
  return { title: NAMES[hospitalId] ? `Waiting room · ${NAMES[hospitalId]}` : "Not found · SevaSetu" };
}

export function generateStaticParams() {
  return HOSPITALS.map((hospitalId) => ({ hospitalId }));
}

export default function BoardPage({ params }: PageProps<"/board/[hospitalId]">) {
  return (
    <Suspense fallback={<div className="min-h-screen bg-[#0b2a3c]" />}>
      <Board params={params} />
    </Suspense>
  );
}

async function Board({ params }: { params: PageProps<"/board/[hospitalId]">["params"] }) {
  const { hospitalId } = await params;
  if (!HOSPITALS.includes(hospitalId)) notFound();
  return <BoardScreen hospitalId={hospitalId} />;
}
