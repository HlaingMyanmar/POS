-- Keep the service_jobs database enum aligned with ServiceJobStatus.
ALTER TABLE service_jobs
    MODIFY COLUMN status ENUM(
        'CANCELLED',
        'COMPLETED',
        'DELIVERED',
        'ASSIGNED',
        'INSPECTING',
        'IN_PROGRESS',
        'RECEIVED',
        'WAITING_PARTS'
    ) NULL;
