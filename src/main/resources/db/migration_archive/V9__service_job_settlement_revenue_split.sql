ALTER TABLE service_job_lines
    ADD COLUMN discount_amount DECIMAL(15, 2) NOT NULL DEFAULT 0 AFTER subtotal;

ALTER TABLE service_jobs
    ADD COLUMN labor_net_amount DECIMAL(15, 2) NULL AFTER net_amount,
    ADD COLUMN parts_net_amount DECIMAL(15, 2) NULL AFTER labor_net_amount,
    ADD COLUMN discount_allocation_method VARCHAR(20) NULL AFTER parts_net_amount;
