"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { apiFetch, errorMessageFromBody, type ApiErrorBody } from "@/lib/api-client";

const CATEGORIES = [
  "Beauty Salon",
  "Clinic / Healthcare",
  "Furniture / Home",
  "Tuition / Education",
  "Retail Shop",
  "Other",
] as const;

type Status = "idle" | "submitting" | "error";

export function OnboardingForm() {
  const router = useRouter();
  const [status, setStatus] = useState<Status>("idle");
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setStatus("submitting");
    setError(null);

    const form = e.currentTarget;
    const fd = new FormData(form);

    try {
      const res = await apiFetch("/onboarding", {
        method: "POST",
        body: fd,
      });
      const data = (await res.json().catch(() => ({}))) as {
        id?: string;
      } & ApiErrorBody;
      if (!res.ok) {
        throw new Error(
          errorMessageFromBody(data, "Could not save onboarding. Try again."),
        );
      }
      if (!data.id) {
        throw new Error("Onboarding response missing id");
      }
      router.push(`/connect?onboardingId=${encodeURIComponent(data.id)}`);
    } catch (err) {
      setStatus("error");
      setError(err instanceof Error ? err.message : "Something went wrong");
    }
  }

  return (
    <form onSubmit={onSubmit} className="mx-auto flex max-w-xl flex-col gap-4">
      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">Phone (WhatsApp)</span>
        <input
          className="field"
          name="phone"
          type="tel"
          required
          placeholder="+91 98XXXXXXXX"
          autoComplete="tel"
        />
      </label>

      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">Business name</span>
        <input
          className="field"
          name="businessName"
          required
          placeholder="e.g. Glow Studio Ludhiana"
          autoComplete="organization"
        />
      </label>

      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">Category</span>
        <select className="field" name="category" required defaultValue="">
          <option value="" disabled>
            Select category
          </option>
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">City</span>
        <input
          className="field"
          name="city"
          required
          placeholder="e.g. Amritsar, Chandigarh, Jaipur"
          autoComplete="address-level2"
        />
      </label>

      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">Bio / short intro</span>
        <textarea
          className="field min-h-[96px] resize-y"
          name="bio"
          required
          placeholder="Aapke salon/clinic ke baare mein 1–2 lines…"
        />
      </label>

      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">
          Optional photo{" "}
          <span className="text-[var(--ink-muted)]">(logo or storefront)</span>
        </span>
        <input
          className="field file:mr-3 file:rounded file:border-0 file:bg-[var(--paper-deep)] file:px-3 file:py-1.5 file:text-sm"
          name="photo"
          type="file"
          accept="image/*"
        />
      </label>

      <p className="text-xs text-[var(--ink-muted)]">
        By continuing you confirm you have consent to use customer photos in
        promotional posts when applicable (DPDP-aware). We never ask for your
        Instagram password.
      </p>

      {error ? (
        <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800" role="alert">
          {error}
        </p>
      ) : null}

      <button
        type="submit"
        className="btn-primary disabled:opacity-60"
        disabled={status === "submitting"}
      >
        {status === "submitting" ? "Saving…" : "Continue to Connect Instagram"}
      </button>
    </form>
  );
}
