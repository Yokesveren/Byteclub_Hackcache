"use client";

import { useEffect, useState } from "react";
import { PREFILL_KEY } from "@/components/landing/RegisterDialog";
import { PageBand } from "@/components/PageBand";
import { Slip } from "@/components/Slip";
import { useShell, type ShellContext } from "@/components/StaffShell";
import { LANGUAGE_NAMES, SPECIAL_NEEDS } from "@/lib/format";
import { rpc, supabase } from "@/lib/supabase";
import type { MyTurn } from "@/lib/types";

export default function CounterPage() {
  return <Counter {...useShell()} />;
}

const AGE_GROUPS = [
  { id: "infant", label: "Infant", hint: "under 1" },
  { id: "child", label: "Child", hint: "1–17" },
  { id: "adult", label: "Adult", hint: "18–59" },
  { id: "elderly", label: "Elderly", hint: "60+" },
];

const AREAS = ["Anantnag", "Baramulla", "Budgam", "Ganderbal", "Kulgam", "Kupwara", "Pulwama", "Shopian", "Srinagar", "Other"];

interface Dept {
  id: string;
  code: string;
  name: string;
}

const empty = {
  firstName: "",
  ageGroup: "adult",
  ageYears: "",
  needs: [] as string[],
  language: "ks",
  phone: "",
  area: "",
  departmentId: "",
  priority: false,
  note: "",
};

function Counter({ staff, hospitalId }: ShellContext) {
  const [departments, setDepartments] = useState<Dept[]>([]);
  const [form, setForm] = useState(empty);
  const [slip, setSlip] = useState<MyTurn | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lookup, setLookup] = useState("");

  useEffect(() => {
    supabase
      .from("departments")
      .select("id, code, name")
      .eq("hospital_id", hospitalId)
      .order("code")
      .then(({ data }) => {
        setDepartments(data ?? []);
        // A name typed on the landing page's "Register a patient" dialog arrives here once.
        try {
          const raw = sessionStorage.getItem(PREFILL_KEY);
          if (raw) {
            sessionStorage.removeItem(PREFILL_KEY);
            const prefill = JSON.parse(raw) as { firstName?: string };
            if (prefill.firstName) setForm((f) => ({ ...f, firstName: prefill.firstName!.slice(0, 60) }));
          }
        } catch {
          /* storage unavailable */
        }
      });
  }, [hospitalId]);

  const set = <K extends keyof typeof empty>(k: K, v: (typeof empty)[K]) => setForm((f) => ({ ...f, [k]: v }));
  const toggleNeed = (id: string) =>
    set("needs", form.needs.includes(id) ? form.needs.filter((n) => n !== id) : [...form.needs, id]);

  const register = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.firstName.trim()) {
      setError("First name is needed for the slip.");
      return;
    }
    if (!departments.some((d) => d.id === form.departmentId)) {
      setError("Choose a department. Not sure? Send the patient to the help desk.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const result = await rpc<MyTurn>("register_visit", {
        p: {
          visit_id: crypto.randomUUID(),
          hospital_id: hospitalId,
          department_id: form.departmentId,
          channel: "counter",
          actor: staff.name,
          suggested_band: form.priority ? "yellow" : "green",
          doctor_note: form.note || null,
          patient: {
            id: crypto.randomUUID(),
            first_name: form.firstName.trim(),
            age_group: form.ageGroup,
            age_years: form.ageYears ? Number(form.ageYears) : null,
            special_needs: form.needs,
            language: form.language,
            phone: form.phone || null,
            area: form.area || null,
            consent: { terms: true, movement: false, via: "counter" },
          },
        },
      });
      setSlip(result);
      setForm({ ...empty, language: form.language });
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  const findSlip = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const found = await rpc<MyTurn | null>("link_slip", { p_hospital_id: hospitalId, p_token: lookup });
      if (!found) setError(`No token ${lookup.toUpperCase()} today at this hospital.`);
      else setSlip(found);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  return (
    <div>
    <PageBand
      tone="sand"
      title="Counter"
      lead="Register a patient who has no phone. The printed slip carries a QR code that works at every scanner and every waypoint."
    />
    <div className="grid gap-6 p-4 sm:p-8 lg:grid-cols-[minmax(0,1fr)_400px]">
      <form onSubmit={register} className="space-y-6 rounded-[24px] bg-white p-6 ring-1 ring-[var(--edge)]">
        <h2 className="text-2xl font-black uppercase tracking-[-0.03em]">Register a patient</h2>

        <Field label="First name">
          <input
            value={form.firstName}
            onChange={(e) => set("firstName", e.target.value)}
            maxLength={60}
            autoFocus
            className="h-14 w-full rounded-xl bg-[#f1f4f6] px-4 text-xl font-bold"
          />
        </Field>

        <Field label="Age">
          <div className="flex flex-wrap gap-2">
            {AGE_GROUPS.map((a) => (
              <Choice key={a.id} active={form.ageGroup === a.id} onClick={() => set("ageGroup", a.id)}>
                {a.label} <span className="text-xs opacity-70">{a.hint}</span>
              </Choice>
            ))}
            <input
              value={form.ageYears}
              onChange={(e) => set("ageYears", e.target.value.replace(/\D/g, "").slice(0, 3))}
              placeholder="years (optional)"
              inputMode="numeric"
              className="h-12 w-40 rounded-xl bg-[#f1f4f6] px-3"
            />
          </div>
        </Field>

        <Field label="Special needs">
          <div className="flex flex-wrap gap-2">
            {SPECIAL_NEEDS.map((n) => (
              <Choice key={n.id} active={form.needs.includes(n.id)} onClick={() => toggleNeed(n.id)}>
                {n.label}
              </Choice>
            ))}
          </div>
        </Field>

        <Field label="Department">
          <div className="flex flex-wrap gap-2">
            {departments.map((d) => (
              <Choice key={d.id} active={form.departmentId === d.id} onClick={() => set("departmentId", d.id)}>
                {d.name}
              </Choice>
            ))}
          </div>
        </Field>

        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Language">
            <select value={form.language} onChange={(e) => set("language", e.target.value)} className="h-12 w-full rounded-xl bg-[#f1f4f6] px-3">
              {Object.entries(LANGUAGE_NAMES).map(([id, name]) => (
                <option key={id} value={id}>
                  {name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Phone for SMS (optional)">
            <input
              value={form.phone}
              onChange={(e) => set("phone", e.target.value.replace(/[^0-9+ ]/g, "").slice(0, 16))}
              inputMode="tel"
              className="h-12 w-full rounded-xl bg-[#f1f4f6] px-3"
            />
          </Field>
          <Field label="Area (optional)">
            <select value={form.area} onChange={(e) => set("area", e.target.value)} className="h-12 w-full rounded-xl bg-[#f1f4f6] px-3">
              <option value="">Skip</option>
              {AREAS.map((a) => (
                <option key={a}>{a}</option>
              ))}
            </select>
          </Field>
        </div>

        <Field label="Short note for the doctor (optional)">
          <input
            value={form.note}
            onChange={(e) => set("note", e.target.value)}
            maxLength={500}
            placeholder="e.g. Cough 5 days, fever"
            className="h-12 w-full rounded-xl bg-[#f1f4f6] px-3"
          />
        </Field>

        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={form.priority} onChange={(e) => set("priority", e.target.checked)} />
          Suggest <strong>Priority</strong> (triage staff confirm or change it). Elderly, infants and pregnant patients are suggested Priority automatically.
        </label>

        <div className="rounded-2xl bg-[#c4242a] p-4 text-sm text-white">
          <strong>Warning signs?</strong> Difficulty breathing, chest pain, fainting, heavy bleeding, fits, sudden weakness, severe injury,
          very sick infant or pregnancy danger signs: <strong>do not register. Send to Emergency now.</strong>
        </div>

        {error && <p className="font-semibold text-red-700">{error}</p>}
        <button disabled={busy} className="min-h-14 w-full rounded-xl bg-[#b0298f] text-lg font-black uppercase tracking-[-0.01em] text-white transition-colors hover:bg-[#951f78] disabled:opacity-60">
          {busy ? "Registering…" : "Register and make slip"}
        </button>
      </form>

      <aside className="space-y-4">
        <form onSubmit={findSlip} className="flex gap-2 rounded-[20px] bg-[#cbe4f2] p-3">
          <input
            value={lookup}
            onChange={(e) => setLookup(e.target.value)}
            placeholder="Find token, e.g. M-017"
            className="h-11 flex-1 rounded-xl bg-white px-3"
          />
          <button className="h-11 rounded-xl bg-[#0a0a0a] px-4 font-bold text-white">Find</button>
        </form>
        {slip ? (
          <>
            <Slip slip={slip} />
            <button onClick={() => window.print()} className="min-h-14 w-full rounded-xl bg-[#0a0a0a] text-lg font-black uppercase tracking-[-0.01em] text-white hover:bg-[#262626]">
              Print slip
            </button>
          </>
        ) : (
          <div className="grid min-h-64 place-items-center rounded-[24px] bg-[#f1e3b5] p-6 text-center">
            <p className="text-2xl font-black uppercase leading-none tracking-[-0.03em]">
              The slip
              <br />
              appears here
            </p>
          </div>
        )}
      </aside>
    </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="mb-2 text-sm font-bold">{label}</p>
      {children}
    </div>
  );
}

function Choice({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`min-h-12 rounded-xl px-4 text-sm font-bold transition-colors active:scale-[0.97] ${
        active ? "bg-[#0a0a0a] text-white" : "bg-[#f1f4f6] text-[#0a0a0a] hover:bg-[#f1e3b5]"
      }`}
    >
      {children}
    </button>
  );
}
