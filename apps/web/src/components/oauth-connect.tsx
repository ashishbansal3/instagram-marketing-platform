"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { DEMO_MODE, PRODUCT_NAME } from "@/lib/brand";

const DEMO_KEY = "ig_demo_meta_tokens";

type DemoTokens = {
  provider: "instagram_login";
  accessToken: string;
  igUserId: string;
  connectedAt: string;
  onboardingId?: string;
};

export function OAuthConnect({ onboardingId }: { onboardingId?: string }) {
  const [tokens, setTokens] = useState<DemoTokens | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(DEMO_KEY);
      if (raw) setTokens(JSON.parse(raw) as DemoTokens);
    } catch {
      /* ignore */
    }
  }, []);

  async function connectDemo() {
    setBusy(true);
    try {
      const res = await fetch("/api/oauth/instagram/demo-connect", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ onboardingId, demoMode: true }),
      });
      const data = (await res.json()) as DemoTokens & { error?: string };
      if (!res.ok) throw new Error(data.error || "Connect failed");
      localStorage.setItem(DEMO_KEY, JSON.stringify(data));
      setTokens(data);
    } catch (e) {
      alert(e instanceof Error ? e.message : "Connect failed");
    } finally {
      setBusy(false);
    }
  }

  function disconnect() {
    localStorage.removeItem(DEMO_KEY);
    setTokens(null);
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
          Return here and connect with Instagram Login (demo mode stores fake
          tokens locally for {PRODUCT_NAME}).
        </li>
      </ol>

      {tokens ? (
        <div className="rounded-lg border border-[var(--line)] bg-white/80 p-5">
          <p className="text-sm font-semibold text-[var(--brand)]">Connected (demo)</p>
          <dl className="mt-3 space-y-1 text-sm text-[var(--ink-muted)]">
            <div className="flex justify-between gap-4">
              <dt>Provider</dt>
              <dd className="font-mono text-[var(--ink)]">{tokens.provider}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt>IG user</dt>
              <dd className="font-mono text-[var(--ink)]">{tokens.igUserId}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt>Token</dt>
              <dd className="truncate font-mono text-[var(--ink)]">{tokens.accessToken}</dd>
            </div>
          </dl>
          <div className="mt-5 flex flex-wrap gap-3">
            <Link href="/dashboard" className="btn-primary">
              Open dashboard
            </Link>
            <button type="button" className="btn-secondary" onClick={disconnect}>
              Disconnect demo
            </button>
          </div>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          <button
            type="button"
            className="btn-primary disabled:opacity-60"
            onClick={connectDemo}
            disabled={busy}
          >
            {busy ? "Connecting…" : "Connect Instagram (demo)"}
          </button>
          {!DEMO_MODE ? (
            <p className="text-xs text-[var(--ink-muted)]">
              Production will redirect to Instagram Business Login OAuth.
            </p>
          ) : (
            <p className="text-xs text-[var(--ink-muted)]">
              Demo mode: no real Meta OAuth. Fake long-lived tokens are stored in
              this browser for walkthroughs.
            </p>
          )}
        </div>
      )}
    </div>
  );
}
