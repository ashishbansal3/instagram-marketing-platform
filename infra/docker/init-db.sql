-- MVP bootstrap schema (subset of technical design)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS tenants (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  slug text UNIQUE NOT NULL,
  status text NOT NULL DEFAULT 'trial',
  plan text NOT NULL DEFAULT 'free',
  free_posts_remaining int NOT NULL DEFAULT 15,
  locale text NOT NULL DEFAULT 'en-IN',
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS clients (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  business_name text NOT NULL,
  category text,
  city text,
  phone_e164 text,
  wa_user_id text,
  onboarding_status text NOT NULL DEFAULT 'started',
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS media_assets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  source text NOT NULL,
  kind text NOT NULL,
  r2_key text,
  instruction_text text,
  wa_media_id text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS posts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  client_id uuid REFERENCES clients(id),
  type text NOT NULL DEFAULT 'image',
  status text NOT NULL DEFAULT 'intake_received',
  caption text,
  instruction_text text,
  feedback_text text,
  retry_count int NOT NULL DEFAULT 0,
  max_retries int NOT NULL DEFAULT 3,
  ig_permalink text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS jobs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  type text NOT NULL,
  status text NOT NULL DEFAULT 'queued',
  idempotency_key text UNIQUE,
  payload jsonb NOT NULL DEFAULT '{}',
  result jsonb,
  attempts int NOT NULL DEFAULT 0,
  last_error text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_clients_wa_user ON clients(wa_user_id);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status, type);
CREATE INDEX IF NOT EXISTS idx_posts_status ON posts(status);

-- Demo tenant for mock WhatsApp flows
INSERT INTO tenants (id, name, slug, status, plan, locale)
VALUES ('11111111-1111-1111-1111-111111111111', 'Demo Salon', 'demo-salon', 'active', 'standard', 'hi-IN')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO clients (id, tenant_id, business_name, category, city, phone_e164, wa_user_id, onboarding_status)
VALUES (
  '22222222-2222-2222-2222-222222222222',
  '11111111-1111-1111-1111-111111111111',
  'Demo Salon Chandigarh',
  'salon',
  'Chandigarh',
  '+919876543210',
  '919876543210',
  'active'
) ON CONFLICT DO NOTHING;
