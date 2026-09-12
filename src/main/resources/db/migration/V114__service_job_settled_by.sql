-- Persist who settled so service vouchers show a fixed cashier name.
ALTER TABLE service_jobs
    ADD COLUMN settled_by VARCHAR(120) NULL;

-- Backfill from the latest SETTLED activity actor (username) for already-settled jobs.
UPDATE service_jobs sj
INNER JOIN (
    SELECT a.service_job_id, a.actor
    FROM service_job_activities a
    INNER JOIN (
        SELECT service_job_id, MAX(id) AS max_id
        FROM service_job_activities
        WHERE event_type = 'SETTLED'
        GROUP BY service_job_id
    ) latest ON latest.max_id = a.id
) s ON s.service_job_id = sj.id
SET sj.settled_by = s.actor
WHERE sj.payment_status IS NOT NULL
  AND (sj.voided IS NULL OR sj.voided = 0)
  AND (sj.settled_by IS NULL OR sj.settled_by = '');
