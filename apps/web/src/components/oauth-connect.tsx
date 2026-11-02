"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { PRODUCT_NAME } from "@/lib/brand";

const SESSION_KEY = "rozpost_meta_connection";

type MetaConnection = {
  provider: "instagram_login";
  accessToken: string;
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
      if (raw) setConnection(JSON.parse(raw) as MetaConnection);
    } catch {
      /* ignore corrupt local session */
    }
  }, []);

  async function connectInstagram() {
    setBusy(true);
    setError(null);
    try {
      const res = await fetch("/api/v1/oauth/instagram/connect", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ onboardingId }),
      });
      const data = (await res.json()) as MetaConnection & {
        error?: { message?: string };
        redirectUrl?: string;
      };
      if (!res.ok) {
        throw new Error(data.error?.message || "Instagram connect failed");
      }
      if (data.redirectUrl) {
        window.location.assign(data.redirectUrl);
        return;
      }
      localStorage.setItem(SESSION_KEY, JSON.stringify(data));
      setConnection(data);
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
          Open the Instagram app → Settings → Account type and tools →{" "}
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
          Return here and connect with Instagram Login. {PRODUCT_NAME} never asks
          for your password.
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
            Live mode redirects to Instagram Business Login. Mock mode
            (`META_MODE=mock`) stores a local session for integration smoke tests
            only — never used as a customer-facing path in production.
          </p>
        </div>
      )}
    </div>
  );
}
