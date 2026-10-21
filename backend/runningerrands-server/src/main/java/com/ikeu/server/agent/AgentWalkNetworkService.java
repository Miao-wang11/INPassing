package com.ikeu.server.agent;

import com.ikeu.server.service.CampusCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Trustworthy campus-specific, administrator-verified walking network. No inferred
 * building coordinates, synthetic edges, or direct map calls from the LLM.
 */
@Service
@RequiredArgsConstructor
public class AgentWalkNetworkService {
    private final JdbcTemplate jdbc;
    private final CampusCatalogService campuses;

    public record Place(Long id, String name, String aliasesJson, BigDecimal latitude,
                        BigDecimal longitude, LocalDateTime verifiedAt) {}
    public record Edge(Long from, Long to, BigDecimal distanceMeters, LocalDateTime verifiedAt) {}
    public record Network(Long campusId, String routeMode, List<Place> places, List<Edge> edges) {}

    @Transactional(readOnly = true)
    public Network verifiedForUser(Long uid) {
        Long campusId = campuses.requireUserCampus(uid);
        List<Place> places = jdbc.query(
                "SELECT id,canonical_name,CAST(aliases_json AS CHAR),latitude,longitude,verified_at " +
                "FROM agent_walk_place WHERE campus_id=? AND active=1 AND verified_at IS NOT NULL " +
                "ORDER BY id LIMIT 200",
                (rs, row) -> new Place(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5),rs.getTimestamp(6).toLocalDateTime()),
                campusId);
        List<Edge> edges = jdbc.query(
                "SELECT e.from_place_id,e.to_place_id,e.distance_meters,e.verified_at " +
                "FROM agent_walk_edge e " +
                "JOIN agent_walk_place p ON p.id=e.from_place_id " +
                "JOIN agent_walk_place q ON q.id=e.to_place_id " +
                "WHERE e.campus_id=? AND p.campus_id=? AND q.campus_id=? " +
                "AND e.active=1 AND p.active=1 AND q.active=1 " +
                "AND e.verified_at IS NOT NULL AND p.verified_at IS NOT NULL AND q.verified_at IS NOT NULL " +
                "ORDER BY e.id LIMIT 1000",
                (rs, row) -> new Edge(rs.getLong(1),rs.getLong(2),
                        rs.getBigDecimal(3),rs.getTimestamp(4).toLocalDateTime()),
                campusId,campusId,campusId);
        return new Network(campusId,"VERIFIED_WALK_EDGES_ONLY",places,edges);
    }
}
