-- Stage 4: optional, audited campus footpath data. NO fabricated seed coordinates or durations.
-- Only campus administrators should import verified places and measured pedestrian edges.
-- MySQL 8.0; apply separately to an isolated test database first.
CREATE TABLE IF NOT EXISTS agent_walk_place (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  campus_id BIGINT UNSIGNED NOT NULL,
  canonical_name VARCHAR(100) NOT NULL,
  aliases_json JSON NOT NULL,
  latitude DECIMAL(10,7) DEFAULT NULL,
  longitude DECIMAL(10,7) DEFAULT NULL,
  source_note VARCHAR(255) NOT NULL,
  verified_at DATETIME NOT NULL,
  active TINYINT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_agent_walk_place (campus_id,canonical_name),
  KEY idx_agent_walk_place_campus (campus_id,active),
  CONSTRAINT chk_agent_walk_place_active CHECK (active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_walk_edge (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  campus_id BIGINT UNSIGNED NOT NULL,
  from_place_id BIGINT NOT NULL,
  to_place_id BIGINT NOT NULL,
  distance_meters DECIMAL(10,2) NOT NULL,
  source_note VARCHAR(255) NOT NULL,
  verified_at DATETIME NOT NULL,
  active TINYINT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_agent_walk_edge (campus_id,from_place_id,to_place_id),
  KEY idx_agent_walk_edge_campus (campus_id,active),
  CONSTRAINT fk_agent_walk_from FOREIGN KEY (from_place_id) REFERENCES agent_walk_place(id),
  CONSTRAINT fk_agent_walk_to FOREIGN KEY (to_place_id) REFERENCES agent_walk_place(id),
  CONSTRAINT chk_agent_walk_edge_positive CHECK (distance_meters > 0),
  CONSTRAINT chk_agent_walk_edge_active CHECK (active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Entries are bidirectional only when both physical directions are walkable.
-- Missing/incomplete networks must be reported UNVERIFIED, NEVER as zero detour.
