-- Local/dev seed for META_MODE=mock smoke tests. Safe in production: ON CONFLICT DO NOTHING
-- Prefer APP_ENV=local when applying in compose; production deploys may leave unused.
INSERT INTO tenants (id, name, slug, status, plan, locale)
VALUES ('11111111-1111-1111-1111-111111111111', 'Local Dev Salon', 'local-dev-salon', 'active', 'standard', 'hi-IN')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO clients (id, tenant_id, business_name, category, city, phone_e164, wa_user_id, onboarding_status)
VALUES (
  '22222222-2222-2222-2222-222222222222',
  '11111111-1111-1111-1111-111111111111',
  'Local Dev Salon Chandigarh',
  'salon',
  'Chandigarh',
  '+919876543210',
  '919876543210',
  'active'
) ON CONFLICT DO NOTHING;
