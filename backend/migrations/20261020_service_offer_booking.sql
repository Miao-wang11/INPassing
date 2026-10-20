-- 2026-10-20 Phase 3: service showcase / directed bookings.
-- Apply ONCE to EXISTING databases after BACKUP; never rerun backend/runningerrands.sql.
CREATE TABLE IF NOT EXISTS service_offer (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  provider_id BIGINT UNSIGNED NOT NULL,
  campus_id BIGINT UNSIGNED NOT NULL,
  category VARCHAR(32) NOT NULL,
  sub_type VARCHAR(32) DEFAULT NULL,
  title VARCHAR(80) NOT NULL,
  description VARCHAR(500) NOT NULL,
  service_price DECIMAL(10,2) NOT NULL,
  available_from DATETIME NOT NULL,
  available_until DATETIME NOT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active; 0 offline',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_offer_hall (campus_id, status, available_until, created_at),
  KEY idx_offer_owner (provider_id, created_at),
  CONSTRAINT fk_offer_user FOREIGN KEY (provider_id) REFERENCES user(id),
  CONSTRAINT fk_offer_campus FOREIGN KEY (campus_id) REFERENCES school_campus(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE task
  ADD COLUMN service_offer_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Reference to service listing for directed booking',
  ADD COLUMN reserved_runner_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Only this runner may accept service-booked task';
CREATE INDEX idx_task_reserved ON task (reserved_runner_id, status);

CREATE TABLE IF NOT EXISTS service_booking (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  offer_id BIGINT UNSIGNED NOT NULL,
  requester_id BIGINT UNSIGNED NOT NULL,
  request_key VARCHAR(64) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  task_id BIGINT UNSIGNED DEFAULT NULL,
  order_id BIGINT UNSIGNED DEFAULT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_booking_request (requester_id, request_key),
  UNIQUE KEY uk_booking_order (order_id),
  KEY idx_booking_offer (offer_id, created_at),
  CONSTRAINT fk_booking_offer FOREIGN KEY (offer_id) REFERENCES service_offer(id),
  CONSTRAINT fk_booking_user FOREIGN KEY (requester_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
