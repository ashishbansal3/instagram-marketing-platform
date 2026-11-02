const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

export default function HomePage() {
  return (
    <main className="hero">
      <p className="brand">RozPost</p>
      <h1 className="headline">WhatsApp photos in. Instagram posts out.</h1>
      <p className="sub">
        Local businesses send product shots on WhatsApp. RozPost polishes, asks for Approve /
        Disapprove, then publishes to Instagram — official Meta APIs only.
      </p>
      <div className="cta-row">
        <a className="cta" href={`${apiBase}/api/health`}>
          API health
        </a>
        <a className="cta-secondary" href="https://github.com/ashishbansal3/instagram-marketing-platform">
          GitHub
        </a>
      </div>
      <p className="meta">Demo mode: META_MODE=mock · docker compose up --build</p>
    </main>
  );
}
