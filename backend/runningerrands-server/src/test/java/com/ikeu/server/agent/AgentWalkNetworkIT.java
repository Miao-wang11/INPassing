package com.ikeu.server.agent;

import com.ikeu.server.service.CampusCatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MySQL test of reviewed, campus-isolated pedestrian graph reads. */
@EnabledIfEnvironmentVariable(named="SHUNSHOU_AGENT_IT_MYSQL_URL",matches=".+")
class AgentWalkNetworkIT {
    @Test
    void onlyAuthenticatedCampusAndActiveVerifiedEdgesCanBeRead() throws Exception {
        var dataSource=new DriverManagerDataSource(
                System.getenv("SHUNSHOU_AGENT_IT_MYSQL_URL"),"agent_test","agent_test_pass");
        var jdbc=new JdbcTemplate(dataSource);
        String ddl=Files.readString(Path.of("..","migrations","20261021_agent_walk_network.sql"));
        // Two CREATE TABLE statements; comments deliberately contain no semicolons.
        jdbc.execute("DROP TABLE IF EXISTS agent_walk_edge");
        jdbc.execute("DROP TABLE IF EXISTS agent_walk_place");
        var cleanDdl=ddl.replaceAll("(?m)^--.*$", "");
        var statements=java.util.Arrays.stream(cleanDdl.split(";"))
                .map(String::trim)
                .filter(s -> s.startsWith("CREATE TABLE")).toList();
        assertEquals(2,statements.size());
        for (String statement:statements) jdbc.execute(statement);
        jdbc.update("INSERT INTO agent_walk_place(campus_id,canonical_name,aliases_json,source_note,verified_at) "+
                "VALUES (7,'图书馆','[\"图书馆正门\"]','test measured evidence',NOW()), "+
                "(7,'宿舍楼','[]','test measured evidence',NOW()), "+
                "(8,'隔壁校区','[]','test measured evidence',NOW())");
        var ids=jdbc.queryForList("SELECT id FROM agent_walk_place ORDER BY id",Long.class);
        jdbc.update("INSERT INTO agent_walk_edge(campus_id,from_place_id,to_place_id,"+
                "distance_meters,source_note,verified_at) VALUES (?,?,?,?,?,NOW())",
                7,ids.get(0),ids.get(1),500,"test walkway evidence");
        jdbc.update("INSERT INTO agent_walk_edge(campus_id,from_place_id,to_place_id,"+
                "distance_meters,source_note,verified_at,active) VALUES (?,?,?,?,?,NOW(),0)",
                7,ids.get(1),ids.get(0),500,"inactive walkway");
        var campuses=mock(CampusCatalogService.class);
        when(campuses.requireUserCampus(9L)).thenReturn(7L);
        var service=new AgentWalkNetworkService(jdbc,campuses);
        var network=service.verifiedForUser(9L);
        assertEquals(7L,network.campusId());
        assertEquals(2,network.places().size());
        assertEquals(1,network.edges().size());
        assertEquals(ids.get(0),network.edges().get(0).from());
        assertEquals(ids.get(1),network.edges().get(0).to());
        assertTrue(network.places().stream().noneMatch(p -> "隔壁校区".equals(p.name())));
        verify(campuses).requireUserCampus(9L);
    }
}
