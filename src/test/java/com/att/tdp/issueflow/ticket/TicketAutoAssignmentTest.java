package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.BaseIntegrationTest;
import com.att.tdp.issueflow.auth.dto.LoginRequest;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketAutoAssignmentTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String adminToken;
    private User admin;
    private Project project;

    @BeforeEach
    void setUp() throws Exception {
        admin = userRepository.save(User.builder()
                .username("admin").email("admin@test.com").fullName("Admin")
                .role(UserRole.ADMIN).password(passwordEncoder.encode("pass")).build());

        project = projectRepository.save(Project.builder()
                .name("Test Project").description("desc").owner(admin).build());

        adminToken = login("admin", "pass");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User saveDeveloper(String username) {
        return userRepository.save(User.builder()
                .username(username).email(username + "@test.com").fullName(username)
                .role(UserRole.DEVELOPER).password(passwordEncoder.encode("pass")).build());
    }

    private String login(String username, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private String postTicket(Long assigneeId) throws Exception {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setTitle("Ticket");
        req.setStatus(TicketStatus.TODO);
        req.setPriority(TicketPriority.MEDIUM);
        req.setType(TicketType.BUG);
        req.setProjectId(project.getId());
        req.setAssigneeId(assigneeId);
        return mockMvc.perform(post("/tickets")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Long postTicketId(Long assigneeId) throws Exception {
        return objectMapper.readTree(postTicket(assigneeId)).get("id").asLong();
    }

    private Ticket saveTicket(Project proj, User assignee, TicketStatus status) {
        return ticketRepository.save(Ticket.builder()
                .title("BG").priority(TicketPriority.LOW)
                .status(status).type(TicketType.BUG)
                .project(proj).assignee(assignee).build());
    }

    private List<Map<String, Object>> autoAssignLogsFor(Long ticketId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE entity_id = ? AND action = 'AUTO_ASSIGN'",
                ticketId);
    }

    // ── assignment: single developer ──────────────────────────────────────────

    @Test
    void create_noAssignee_oneDeveloper_assignsToThatDeveloper() throws Exception {
        User dev = saveDeveloper("dev1");

        String body = postTicket(null);

        assertThat(objectMapper.readTree(body).get("assigneeId").asLong()).isEqualTo(dev.getId());
    }

    // ── assignment: no developers ─────────────────────────────────────────────

    @Test
    void create_noAssignee_noDevelopers_assigneeRemainsNull() throws Exception {
        String body = postTicket(null);

        assertThat(objectMapper.readTree(body).get("assigneeId").isNull()).isTrue();
    }

    // ── assignment: explicit assigneeId bypasses auto-assignment ─────────────

    @Test
    void create_withExplicitAssignee_usedDirectly() throws Exception {
        User dev1 = saveDeveloper("dev1");
        User dev2 = saveDeveloper("dev2");

        String body = postTicket(dev2.getId());

        assertThat(objectMapper.readTree(body).get("assigneeId").asLong()).isEqualTo(dev2.getId());
    }

    // ── assignment: picks least-loaded developer ──────────────────────────────

    @Test
    void create_noAssignee_picksLeastLoadedDeveloper() throws Exception {
        User dev1 = saveDeveloper("dev1");
        User dev2 = saveDeveloper("dev2");
        saveTicket(project, dev1, TicketStatus.TODO);
        saveTicket(project, dev1, TicketStatus.IN_PROGRESS);
        // dev2 has 0 open tickets

        String body = postTicket(null);

        assertThat(objectMapper.readTree(body).get("assigneeId").asLong()).isEqualTo(dev2.getId());
    }

    // ── assignment: tie-break by lowest id ───────────────────────────────────

    @Test
    void create_noAssignee_tiesResolvedByLowestId() throws Exception {
        User dev1 = saveDeveloper("dev1"); // created first → lower id
        User dev2 = saveDeveloper("dev2"); // both have 0 open tickets

        String body = postTicket(null);

        assertThat(objectMapper.readTree(body).get("assigneeId").asLong()).isEqualTo(dev1.getId());
    }

    // ── assignment: DONE tickets don't count toward workload ──────────────────

    @Test
    void create_noAssignee_doneTicketsExcludedFromWorkload() throws Exception {
        User dev1 = saveDeveloper("dev1");
        User dev2 = saveDeveloper("dev2");
        // dev1: 1 open + 5 DONE → effective load 1
        saveTicket(project, dev1, TicketStatus.TODO);
        for (int i = 0; i < 5; i++) saveTicket(project, dev1, TicketStatus.DONE);
        // dev2: 2 open → effective load 2
        saveTicket(project, dev2, TicketStatus.TODO);
        saveTicket(project, dev2, TicketStatus.IN_PROGRESS);

        String body = postTicket(null);

        assertThat(objectMapper.readTree(body).get("assigneeId").asLong()).isEqualTo(dev1.getId());
    }

    // ── assignment: soft-deleted tickets don't count toward workload ──────────

    @Test
    void create_noAssignee_softDeletedTicketsExcludedFromWorkload() throws Exception {
        User dev1 = saveDeveloper("dev1");
        User dev2 = saveDeveloper("dev2");
        // dev1: 1 open + 5 soft-deleted → effective load 1
        saveTicket(project, dev1, TicketStatus.TODO);
        for (int i = 0; i < 5; i++) {
            Ticket t = saveTicket(project, dev1, TicketStatus.TODO);
            t.setDeletedAt(LocalDateTime.now());
            ticketRepository.save(t);
        }
        // dev2: 2 open → effective load 2
        saveTicket(project, dev2, TicketStatus.TODO);
        saveTicket(project, dev2, TicketStatus.TODO);

        String body = postTicket(null);

        assertThat(objectMapper.readTree(body).get("assigneeId").asLong()).isEqualTo(dev1.getId());
    }

    // ── assignment: only tickets in the same project count ───────────────────

    @Test
    void create_noAssignee_ticketsInOtherProjectIgnored() throws Exception {
        User dev1 = saveDeveloper("dev1");
        User dev2 = saveDeveloper("dev2");

        // dev1 has 5 open tickets in a different project — load in *this* project is 0
        Project other = projectRepository.save(Project.builder()
                .name("Other").description("d").owner(admin).build());
        for (int i = 0; i < 5; i++) saveTicket(other, dev1, TicketStatus.TODO);

        // dev2 has 1 open ticket in this project
        saveTicket(project, dev2, TicketStatus.TODO);

        // dev1 wins: 0 open in project vs dev2's 1
        String body = postTicket(null);

        assertThat(objectMapper.readTree(body).get("assigneeId").asLong()).isEqualTo(dev1.getId());
    }

    // ── audit log ─────────────────────────────────────────────────────────────

    @Test
    void create_noAssignee_writesAutoAssignAuditLog() throws Exception {
        saveDeveloper("dev1"); // 0 open tickets at assignment time

        Long ticketId = postTicketId(null);

        List<Map<String, Object>> logs = autoAssignLogsFor(ticketId);
        assertThat(logs).hasSize(1);
        Map<String, Object> log = logs.get(0);
        assertThat(log.get("action")).isEqualTo("AUTO_ASSIGN");
        assertThat(log.get("entity_type")).isEqualTo("TICKET");
        assertThat(log.get("actor")).isEqualTo("SYSTEM");
        assertThat(log.get("performed_by")).isNull();
        assertThat(log.get("details").toString())
                .contains("dev1")
                .contains("0 open tickets");
    }

    @Test
    void create_withExplicitAssignee_noAutoAssignLog() throws Exception {
        User dev = saveDeveloper("dev1");

        Long ticketId = postTicketId(dev.getId());

        assertThat(autoAssignLogsFor(ticketId)).isEmpty();
    }

    @Test
    void create_noAssignee_noDevelopers_noAutoAssignLog() throws Exception {
        Long ticketId = postTicketId(null);

        assertThat(autoAssignLogsFor(ticketId)).isEmpty();
    }

    // ── workload endpoint ─────────────────────────────────────────────────────

    @Test
    void getWorkload_returnsDevelopersWithCorrectCounts() throws Exception {
        User dev1 = saveDeveloper("dev1");
        User dev2 = saveDeveloper("dev2");
        saveTicket(project, dev1, TicketStatus.TODO);
        saveTicket(project, dev1, TicketStatus.IN_PROGRESS);
        // dev2 has 0 tickets

        mockMvc.perform(get("/projects/" + project.getId() + "/workload")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username").value("dev1"))
                .andExpect(jsonPath("$[0].openTicketCount").value(2))
                .andExpect(jsonPath("$[1].username").value("dev2"))
                .andExpect(jsonPath("$[1].openTicketCount").value(0));
    }

    @Test
    void getWorkload_projectNotFound_returns404() throws Exception {
        mockMvc.perform(get("/projects/9999/workload")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getWorkload_noDevelopers_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/projects/" + project.getId() + "/workload")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getWorkload_excludesDoneAndDeletedTickets() throws Exception {
        User dev = saveDeveloper("dev1");
        saveTicket(project, dev, TicketStatus.TODO);
        saveTicket(project, dev, TicketStatus.IN_PROGRESS);
        for (int i = 0; i < 3; i++) saveTicket(project, dev, TicketStatus.DONE);
        for (int i = 0; i < 4; i++) {
            Ticket t = saveTicket(project, dev, TicketStatus.TODO);
            t.setDeletedAt(LocalDateTime.now());
            ticketRepository.save(t);
        }

        mockMvc.perform(get("/projects/" + project.getId() + "/workload")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].openTicketCount").value(2));
    }

    @Test
    void getWorkload_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/projects/" + project.getId() + "/workload"))
                .andExpect(status().isUnauthorized());
    }
}
