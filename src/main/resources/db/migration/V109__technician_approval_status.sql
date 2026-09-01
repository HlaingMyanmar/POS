ALTER TABLE service_jobs
    ADD COLUMN final_approval_status BIT NOT NULL DEFAULT 0,
    ADD COLUMN final_approved_by VARCHAR(120) NULL,
    ADD COLUMN final_approved_at DATETIME(6) NULL;

ALTER TABLE service_job_assignments
    ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN approved_by VARCHAR(100) NULL,
    ADD COLUMN approved_at DATETIME NULL;