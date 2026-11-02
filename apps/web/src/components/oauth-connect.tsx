"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { PRODUCT_NAME } from "@/lib/brand";
import { apiFetch, errorMessageFromBody, type ApiErrorBody } from "@/lib/api-client";

const SESSION_KEY = "rozpost_meta_connection";

type MetaConnection = {
  provider: "instagram_login";
  igUserId: string;
  connectedAt: string;
  onboardingId?: string;
  metaMode: "mock" | "live";
};

export function OAuthConnect({ onboardingId }: { onboardingId?: string }) {
  const [connection, setConnection] = useState<MetaConnection | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(SESSION_KEY);
      if (raw) {
        const parsed = JSON.parse(raw) as MetaConnection & { accessToken?: string };
        // Never persist/display raw Meta tokens in the UI.
        setConnection({
          provider: parsed.provider,
          igUserId: parsed.igUserId,
          connectedAt: parsed.connectedAt,
          onboardingId: parsed.onboardingId,
          metaMode: parsed.metaMode,
        });
      }
    } catch {
      /* ignore */
    }
  }, []);

  async function connectInstagram() {
    setBusy(true);
    setError(null);
    try {
      const res = await apiFetch("/oauth/instagram/connect", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ onboardingId }),
      });
      const data = (await res.json()) as MetaConnection &
        ApiErrorBody & { redirectUrl?: string; accessToken?: string };
      if (!res.ok) {
        throw new Error(errorMessageFromBody(data, "Instagram connect failed"));
      }
      if (data.redirectUrl) {
        window.location.assign(data.redirectUrl);
        return;
      }
      const safe: MetaConnection = {
        provider: data.provider,
        igUserId: data.igUserId,
        connectedAt: data.connectedAt,
        onboardingId: data.onboardingId,
        metaMode: data.metaMode,
      };
      localStorage.setItem(SESSION_KEY, JSON.stringify(safe));
      setConnection(safe);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Connect failed");
    } finally {
      setBusy(false);
    }
  }

  function disconnect() {
    localStorage.removeItem(SESSION_KEY);
    setConnection(null);
  }

  return (
    <div className="mx-auto flex max-w-xl flex-col gap-6">
      <ol className="list-decimal space-y-3 pl-5 text-sm text-[var(--ink-muted)]">
        <li>
          Open Instagram → Settings → Account type and tools →{" "}
          <strong className="text-[var(--ink)]">Switch to professional account</strong>.
        </li>
        <li>
          Official help:{" "}
          <a
            className="underline underline-offset-2"
            href="https://help.instagram.com/502981923235522"
            target="_blank"
            rel="noreferrer"
          >
            Instagram Business account setup
          </a>
          .
        </li>
        <li>
          Return here and connect. {PRODUCT_NAME} never asks for your password
          and never shows raw access tokens in the UI.
        </li>
      </ol>

      {error ? (
        <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800" role="alert">
          {error}
        </p>
      ) : null}

      {connection ? (
        <div className="rounded-lg border border-[var(--line)] bg-white/80 p-5">
          <p className="text-sm font-semibold text-[var(--brand)]">
            Instagram connected
            {connection.metaMode === "mock" ? " (META_MODE=mock)" : ""}
          </p>
          <dl className="mt-3 space-y-1 text-sm text-[var(--ink-muted)]">
            <div className="flex justify-between gap-4">
              <dt>Provider</dt>
              <dd className="font-mono text-[var(--ink)]">{connection.provider}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt>IG user</dt>
              <dd className="font-mono text-[var(--ink)]">{connection.igUserId}</dd>
            </div>
          </dl>
          <div className="mt-5 flex flex-wrap gap-3">
            <Link href="/dashboard" className="btn-primary">
              Open dashboard
            </Link>
            <button type="button" className="btn-secondary" onClick={disconnect}>
              Disconnect
            </button>
          </div>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          <button
            type="button"
            className="btn-primary disabled:opacity-60"
            onClick={connectInstagram}
            disabled={busy}
          >
            {busy ? "Connecting…" : "Connect Instagram"}
          </button>
          <p className="text-xs text-[var(--ink-muted)]">
            Production uses Instagram Business Login (`META_MODE=live`). Mock
            mode is for local smoke tests only.
          </p>
        </div>
      )}
    </div>
  );
}
