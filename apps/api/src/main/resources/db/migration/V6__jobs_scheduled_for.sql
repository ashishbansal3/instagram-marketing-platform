-- Publish worker DB fallback filters on jobs.scheduled_for (quota deferral).
-- Without this column every poll errors and floods postgres logs in e2e.
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS scheduled_for timestamptz;

CREATE INDEX IF NOT EXISTS idx_jobs_queued_scheduled
  ON jobs (type, status, scheduled_for)
  WHERE status = 'queued';
