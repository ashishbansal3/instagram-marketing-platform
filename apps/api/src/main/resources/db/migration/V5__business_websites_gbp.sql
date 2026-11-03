-- Onboarding website generation + Google Business Profile setup records

ALTER TABLE clients ADD COLUMN IF NOT EXISTS bio text;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS services text;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS address text;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS hours_json jsonb;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS latitude double precision;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS longitude double precision;

CREATE TABLE IF NOT EXISTS business_websites (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  client_id uuid NOT NULL REFERENCES clients(id),
  slug text NOT NULL,
  status text NOT NULL DEFAULT 'pending',
  template_id text NOT NULL DEFAULT 'local-professional-v1',
  storage_backend text NOT NULL DEFAULT 'local',
  storage_key text,
  preview_url text,
  html_sha256 text,
  generation_mode text NOT NULL DEFAULT 'template',
  error_message text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (client_id)
);

CREATE INDEX IF NOT EXISTS idx_business_websites_tenant ON business_websites(tenant_id);
CREATE INDEX IF NOT EXISTS idx_business_websites_slug ON business_websites(slug);

CREATE TABLE IF NOT EXISTS gbp_profiles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  client_id uuid NOT NULL REFERENCES clients(id),
  status text NOT NULL DEFAULT 'pending',
  mode text NOT NULL DEFAULT 'mock',
  google_location_name text,
  website_url text,
  business_name text,
  category text,
  phone_e164 text,
  description text,
  address text,
  city text,
  latitude double precision,
  longitude double precision,
  hours_json jsonb,
  setup_payload jsonb NOT NULL DEFAULT '{}',
  verification_checklist jsonb NOT NULL DEFAULT '[]',
  error_message text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (client_id)
);

CREATE INDEX IF NOT EXISTS idx_gbp_profiles_tenant ON gbp_profiles(tenant_id);
CREATE INDEX IF NOT EXISTS idx_gbp_profiles_status ON gbp_profiles(status);
