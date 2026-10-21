package com.ikeu.server.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikeu.model.entity.Task;
import com.ikeu.model.entity.User;
import com.ikeu.model.entity.UserAddress;
import com.ikeu.server.mapper.UserAddressMapper;
import com.ikeu.server.mapper.UserMapper;
import com.ikeu.server.service.CampusCatalogService;
import com.ikeu.server.service.ServiceOfferService;
import com.ikeu.server.service.TaskService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Real InnoDB transaction/row lock test. Runs in CI with isolated MySQL service.
 * TaskService is mocked deliberately: verifies once-per-operation boundary, not wallet accounting.
 */
@EnabledIfEnvironmentVariable(named = "SHUNSHOU_AGENT_IT_MYSQL_URL", matches = ".+")
class AgentMysqlIdempotencyIT {
    @Test
    void concurrentConfirmCallsOriginalServiceOnlyOnce() throws Exception {
        String url = System.getenv("SHUNSHOU_AGENT_IT_MYSQL_URL");
        DriverManagerDataSource ds = new DriverManagerDataSource(url, "agent_test", "agent_test_pass");
        JdbcTemplate db = new JdbcTemplate(ds);
        db.execute("DROP TABLE IF EXISTS agent_operation");
        String ddl = Files.readString(Path.of("..","migrations","20261021_agent_operation.sql"));
        db.execute(ddl.substring(ddl.indexOf("CREATE TABLE")).replaceFirst(";\\s*$", ""));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var campus = mock(CampusCatalogService.class);
        var users = mock(UserMapper.class);
        var addresses = mock(UserAddressMapper.class);
        var tasks = mock(TaskService.class);
        var offers = mock(ServiceOfferService.class);
        when(campus.requireUserCampus(1L)).thenReturn(7L);
        when(users.selectById(1L)).thenReturn(User.builder().id(1L).balance(new BigDecimal("60.00")).build());
        when(addresses.selectById(10L)).thenReturn(UserAddress.builder().id(10L).userId(1L).build());
        AtomicInteger execution = new AtomicInteger();
        when(tasks.publishTask(eq(1L), any())).thenAnswer(inv -> {
            execution.incrementAndGet();
            Thread.sleep(150);
            return Task.builder().id(901L).build();
        });
        var drafts = new AgentDraftService(db,campus,tasks,offers,users,addresses,
                new ObjectMapper(),Validation.buildDefaultValidatorFactory().getValidator());
        var input = new AgentDraftService.DraftInput("PUBLIC_TASK",
                new AgentDraftService.TaskInput("校内代办","资料打印","打印资料","图书馆",
                        10L,null,new BigDecimal("5.00"),BigDecimal.ZERO,BigDecimal.ZERO,60),null);
        AgentDraftService.Prepared preview = tx.execute(status -> drafts.prepare(1L,input));
        assertNotNull(preview);
        assertEquals(0,execution.get()); // no task creation, booking or debit at preview
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> tx.execute(status -> drafts.confirm(1L,
                    preview.operationId(), preview.confirmToken(), "test-pin")));
            var b = pool.submit(() -> tx.execute(status -> drafts.confirm(1L,
                    preview.operationId(), preview.confirmToken(), "test-pin")));
            assertEquals(901L,a.get(15,TimeUnit.SECONDS).taskId());
            assertEquals(901L,b.get(15,TimeUnit.SECONDS).taskId());
            assertEquals(1,execution.get());
            assertEquals("SUCCEEDED", db.queryForObject(
                    "SELECT status FROM agent_operation WHERE operation_id=?", String.class,preview.operationId()));
            assertEquals(901L,db.queryForObject(
                    "SELECT task_id FROM agent_operation WHERE operation_id=?", Long.class,preview.operationId()));
            // Secrets are NOT in persisted draft JSON.
            String stored = db.queryForObject(
                    "SELECT CAST(payload_json AS CHAR) FROM agent_operation WHERE operation_id=?",
                    String.class,preview.operationId());
            assertFalse(stored.contains("test-pin"));
            assertFalse(stored.contains(preview.confirmToken()));
        } finally {
            pool.shutdownNow();
        }
    }
}
