ALTER TABLE service_jobs
    ADD COLUMN lead_final_check_status BIT NOT NULL DEFAULT 0,
    ADD COLUMN lead_final_checked_by VARCHAR(120) NULL,
    ADD COLUMN lead_final_checked_at DATETIME(6) NULL,
    ADD COLUMN lead_final_check_note TEXT NULL,
    ADD COLUMN final_return_reason TEXT NULL;

ALTER TABLE service_job_assignment_logs
    ADD COLUMN completed_work TEXT NULL,
    ADD COLUMN service_details TEXT NULL,
    ADD COLUMN parts_details TEXT NULL;

ALTER TABLE company_settings
    ADD COLUMN service_supervisor_approval_required BIT NOT NULL DEFAULT 1;
