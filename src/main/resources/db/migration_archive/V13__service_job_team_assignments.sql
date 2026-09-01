CREATE TABLE service_job_assignments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    service_job_id INT NOT NULL,
    staff_id INT NOT NULL,
    assignment_role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    task_description TEXT NULL,
    completion_note TEXT NULL,
    assigned_by VARCHAR(100) NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at DATETIME NULL,
    work_started_at DATETIME NULL,
    last_action_at DATETIME NULL,
    completed_at DATETIME NULL,
    ended_at DATETIME NULL,
    accumulated_minutes BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    KEY idx_job_assignment_job (service_job_id),
    KEY idx_job_assignment_staff_status (staff_id, status),
    KEY idx_job_assignment_role_status (service_job_id, assignment_role, status),
    CONSTRAINT fk_job_assignment_job FOREIGN KEY (service_job_id) REFERENCES service_jobs (id),
    CONSTRAINT fk_job_assignment_staff FOREIGN KEY (staff_id) REFERENCES staff (id)
);

CREATE TABLE service_job_assignment_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    assignment_id INT NOT NULL,
    action VARCHAR(20) NOT NULL,
    note TEXT NULL,
    actor VARCHAR(100) NULL,
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_assignment_log_assignment (assignment_id, occurred_at),
    CONSTRAINT fk_assignment_log_assignment FOREIGN KEY (assignment_id)
        REFERENCES service_job_assignments (id)
);

CREATE TABLE service_job_handovers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    service_job_id INT NOT NULL,
    from_assignment_id INT NOT NULL,
    to_staff_id INT NOT NULL,
    assignment_role VARCHAR(20) NOT NULL,
    completed_work TEXT NULL,
    remaining_work TEXT NOT NULL,
    diagnosis_note TEXT NULL,
    status VARCHAR(20) NOT NULL,
    requested_by VARCHAR(100) NULL,
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acted_by VARCHAR(100) NULL,
    acted_at DATETIME NULL,
    rejection_reason TEXT NULL,
    successor_assignment_id INT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    KEY idx_job_handover_job (service_job_id, requested_at),
    KEY idx_job_handover_target_status (to_staff_id, status),
    CONSTRAINT fk_job_handover_job FOREIGN KEY (service_job_id) REFERENCES service_jobs (id),
    CONSTRAINT fk_job_handover_from FOREIGN KEY (from_assignment_id) REFERENCES service_job_assignments (id),
    CONSTRAINT fk_job_handover_target FOREIGN KEY (to_staff_id) REFERENCES staff (id),
    CONSTRAINT fk_job_handover_successor FOREIGN KEY (successor_assignment_id)
        REFERENCES service_job_assignments (id)
);

INSERT INTO service_job_assignments
    (service_job_id, staff_id, assignment_role, status, task_description,
     assigned_by, assigned_at, accepted_at, last_action_at)
SELECT sj.id, sj.assigned_staff_id, 'LEAD', 'ACTIVE', 'Primary technician',
       'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM service_jobs sj
WHERE sj.assigned_staff_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM service_job_assignments a
      WHERE a.service_job_id = sj.id AND a.assignment_role = 'LEAD'
  );

INSERT INTO service_job_assignments
    (service_job_id, staff_id, assignment_role, status, task_description,
     assigned_by, assigned_at, accepted_at, last_action_at)
SELECT sj.id, sj.helper_staff_id, 'HELPER', 'ACTIVE', 'Helper technician',
       'SYSTEM_MIGRATION', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM service_jobs sj
WHERE sj.helper_staff_id IS NOT NULL
  AND sj.helper_staff_id <> sj.assigned_staff_id
  AND NOT EXISTS (
      SELECT 1 FROM service_job_assignments a
      WHERE a.service_job_id = sj.id
        AND a.staff_id = sj.helper_staff_id
        AND a.assignment_role = 'HELPER'
  );
