package com.att.tdp.issueflow;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM ticket_dependencies");
        jdbcTemplate.execute("DELETE FROM attachments");
        jdbcTemplate.execute("DELETE FROM comment_mentions");
        jdbcTemplate.execute("DELETE FROM audit_logs");
        jdbcTemplate.execute("DELETE FROM comments");
        jdbcTemplate.execute("DELETE FROM tickets");
        jdbcTemplate.execute("DELETE FROM projects");
        jdbcTemplate.execute("DELETE FROM users");
    }
}
