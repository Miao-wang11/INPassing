-- Phase 4: one open-or-resolved aftersale case per execution order.
-- Apply once AFTER 20261020_service_offer_booking.sql on an existing database.
CREATE TABLE IF NOT EXISTS order_dispute (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NOT NULL,
  task_id BIGINT UNSIGNED NOT NULL,
  applicant_id BIGINT UNSIGNED NOT NULL,
  reason VARCHAR(500) NOT NULL,
  evidence_json JSON NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 open, 2 rejected/resume, 3 full simulated refund',
  resolution_note VARCHAR(500) DEFAULT NULL,
  decided_by BIGINT UNSIGNED DEFAULT NULL,
  decided_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dispute_order (order_id),
  KEY idx_dispute_status (status, created_at),
  KEY idx_dispute_applicant (applicant_id, created_at),
  CONSTRAINT fk_dispute_order FOREIGN KEY (order_id) REFERENCES task_order(id),
  CONSTRAINT fk_dispute_task FOREIGN KEY (task_id) REFERENCES task(id),
  CONSTRAINT fk_dispute_applicant FOREIGN KEY (applicant_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dispute_event (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  dispute_id BIGINT UNSIGNED NOT NULL,
  actor_id BIGINT UNSIGNED NOT NULL,
  actor_type VARCHAR(12) NOT NULL,
  event_type VARCHAR(30) NOT NULL,
  detail VARCHAR(500) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_dispute_event (dispute_id, created_at),
  CONSTRAINT fk_dispute_event FOREIGN KEY (dispute_id) REFERENCES order_dispute(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
