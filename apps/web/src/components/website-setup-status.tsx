"use client";

import { useEffect, useState } from "react";
import { apiFetch, errorMessageFromBody, type ApiErrorBody } from "@/lib/api-client";

type SetupStatus = {
  websitePreviewUrl?: string | null;
  websiteStatus?: string | null;
  gbpStatus?: string | null;
  gbpMode?: string | null;
  gbpVerificationChecklist?: Array<{
    code?: string;
    label?: string;
    severity?: string;
    status?: string;
  }>;
};

export function WebsiteSetupStatus({ onboardingId }: { onboardingId?: string }) {
  const [status, setStatus] = useState<SetupStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!onboardingId) return;
    let cancelled = false;
    let tries = 0;

    async function poll() {
      tries += 1;
      try {
        const res = await apiFetch(
          `/onboarding?id=${encodeURIComponent(onboardingId!)}`,
          { method: "GET" },
        );
        const data = (await res.json()) as SetupStatus & ApiErrorBody;
        if (!res.ok) {
          throw new Error(errorMessageFromBody(data, "Could not load website setup status"));
        }
        if (!cancelled) {
          setStatus(data);
          setError(null);
        }
        const ready =
          data.websiteStatus === "ready" ||
          data.websitePreviewUrl ||
          data.gbpStatus === "ready_mock" ||
          data.gbpStatus === "created";
        if (!ready && tries < 20 && !cancelled) {
          window.setTimeout(poll, 1500);
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : "Status poll failed");
          if (tries < 8) {
            window.setTimeout(poll, 2000);
          }
        }
      }
    }

    poll();
    return () => {
      cancelled = true;
    };
  }, [onboardingId]);

  if (!onboardingId) {
    return null;
  }

  const checklist = status?.gbpVerificationChecklist ?? [];

  return (
    <section className="mt-10 section-rule pt-10">
      <h2 className="text-xl font-semibold">Your business website &amp; Google listing</h2>
      <p className="mt-2 max-w-xl text-sm text-[var(--ink-muted)]">
        After onboarding we generate a professional site from your profile and prepare a Google
        Business Profile setup record (mock locally; live API when configured).
      </p>

      {error ? (
        <p className="mt-4 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-900" role="status">
          {error}
        </p>
      ) : null}

      <div className="mt-4 space-y-2 text-sm">
        <p>
          <span className="font-medium">Website:</span>{" "}
          {status?.websiteStatus ?? "pending"}
          {status?.websitePreviewUrl ? (
            <>
              {" · "}
              <a
                className="font-semibold text-[var(--brand)] underline underline-offset-2"
                href={status.websitePreviewUrl}
                target="_blank"
                rel="noreferrer"
              >
                Open preview
              </a>
            </>
          ) : (
            <span className="text-[var(--ink-muted)]"> · generating…</span>
          )}
        </p>
        <p>
          <span className="font-medium">Google Business Profile:</span>{" "}
          {status?.gbpStatus ?? "pending"}
          {status?.gbpMode ? ` (${status.gbpMode})` : ""}
        </p>
      </div>

      {checklist.length > 0 ? (
        <ul className="mt-4 list-disc space-y-1 pl-5 text-sm text-[var(--ink-muted)]">
          {checklist.slice(0, 6).map((item) => (
            <li key={item.code ?? item.label}>
              {item.label}
              {item.status ? ` — ${item.status}` : ""}
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}
