-- WhatsApp intake hardening: durable idempotency + media routing columns.
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS max_attempts INT NOT NULL DEFAULT 5;

ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS client_id UUID REFERENCES clients(id);
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS wa_message_id TEXT;
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS mime_type TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_media_assets_wa_message
  ON media_assets(wa_message_id) WHERE wa_message_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_clients_phone
  ON clients(phone_e164) WHERE phone_e164 IS NOT NULL;

CREATE TABLE IF NOT EXISTS webhook_receipts (
  channel TEXT NOT NULL,
  external_message_id TEXT NOT NULL,
  tenant_id UUID,
  correlation_id TEXT,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (channel, external_message_id)
);
