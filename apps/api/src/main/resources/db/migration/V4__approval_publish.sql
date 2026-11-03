-- Approval FSM + IG publish columns/tables (additive on V1 core)

ALTER TABLE posts ADD COLUMN IF NOT EXISTS source_asset_ids uuid[];
ALTER TABLE posts ADD COLUMN IF NOT EXISTS output_asset_ids uuid[];
ALTER TABLE posts ADD COLUMN IF NOT EXISTS scheduled_for timestamptz;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS published_at timestamptz;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS ig_container_id text;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS ig_media_id text;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS approvals (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  post_id uuid NOT NULL REFERENCES posts(id),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  channel text NOT NULL DEFAULT 'whatsapp',
  wa_message_id text,
  decision text NOT NULL,
  note text,
  decided_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS outbound_messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  client_id uuid,
  post_id uuid,
  channel text NOT NULL DEFAULT 'whatsapp',
  body text NOT NULL,
  fixture_path text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS meta_connections (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  provider text NOT NULL,
  ig_user_id text,
  page_id text,
  access_token_enc bytea,
  status text NOT NULL DEFAULT 'active',
  UNIQUE (tenant_id, provider)
);

CREATE TABLE IF NOT EXISTS publish_quota_ledger (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  ig_user_id text NOT NULL,
  published_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS domain_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid,
  event_type text NOT NULL,
  aggregate_type text NOT NULL,
  aggregate_id uuid NOT NULL,
  correlation_id text,
  payload jsonb NOT NULL DEFAULT '{}',
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_domain_events_type ON domain_events(event_type, created_at);
CREATE INDEX IF NOT EXISTS idx_quota_ig_user ON publish_quota_ledger(ig_user_id, published_at);

INSERT INTO meta_connections (tenant_id, provider, ig_user_id, access_token_enc, status)
VALUES (
  '11111111-1111-1111-1111-111111111111',
  'instagram_login',
  'mock-ig-919876543210',
  convert_to('mock-token', 'UTF8'),
  'active'
) ON CONFLICT (tenant_id, provider) DO NOTHING;
