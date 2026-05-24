package com.att.tdp.issueflow.auditlog;

import com.att.tdp.issueflow.BaseIntegrationTest;
import com.att.tdp.issueflow.auth.dto.LoginRequest;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;
import com.att.tdp.issueflow.comment.dto.UpdateCommentRequest;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.project.dto.CreateProjectRequest;
import com.att.tdp.issueflow.project.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserRole;
import com.att.tdp.issueflow.user.dto.CreateUserRequest;
import com.att.tdp.issueflow.user.dto.UpdateUserRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditLogControllerTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User admin;
    private Long adminId;
    private String adminToken;
    private Project project;
    private Ticket ticket;

    @BeforeEach
    void setUp() throws Exception {
        admin = userRepository.save(User.builder()
                .username("admin").email("admin@test.com").fullName("Admin User")
                .role(UserRole.ADMIN).password(passwordEncoder.encode("pass")).build());
        adminId = admin.getId();

        userRepository.save(User.builder()
                .username("dev").email("dev@test.com").fullName("Developer")
                .role(UserRole.DEVELOPER).password(passwordEncoder.encode("pass")).build());

        project = projectRepository.save(Project.builder()
                .name("Test Project").description("desc").owner(admin).build());

        ticket = ticketRepository.save(Ticket.builder()
                .title("Test Ticket").priority(TicketPriority.MEDIUM)
                .status(TicketStatus.TODO).type(TicketType.BUG)
                .project(project).build());

        adminToken = login("admin", "pass");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

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

    private String bearer() { return "Bearer " + adminToken; }

    private JsonNode fetchLog(String action, String entityType, Long entityId) throws Exception {
        String body = mockMvc.perform(get("/audit-logs")
                        .param("action", action)
                        .param("entityType", entityType)
                        .param("entityId", String.valueOf(entityId))
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data).hasSize(1);
        return data.get(0);
    }

    private void assertAuditLog(JsonNode log, String action, String entityType,
                                 Long entityId, String actor, Long performedBy) {
        assertThat(log.get("action").asText()).isEqualTo(action);
        assertThat(log.get("entityType").asText()).isEqualTo(entityType);
        assertThat(log.get("entityId").asLong()).isEqualTo(entityId);
        assertThat(log.get("actor").asText()).isEqualTo(actor);
        if (performedBy == null) {
            assertThat(log.get("performedBy").isNull()).isTrue();
        } else {
            assertThat(log.get("performedBy").asLong()).isEqualTo(performedBy);
        }
    }

    // ── user audit entries ────────────────────────────────────────────────────

    @Test
    void createUser_withAuth_logsCreate_actorUserPerformedByAdmin() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("newuser"); req.setEmail("new@test.com");
        req.setFullName("New User"); req.setPassword("password");
        req.setRole(UserRole.DEVELOPER);

        String body = mockMvc.perform(post("/users")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long userId = objectMapper.readTree(body).get("id").asLong();

        JsonNode log = fetchLog("CREATE", "USER", userId);
        assertAuditLog(log, "CREATE", "USER", userId, "USER", adminId);
    }

    @Test
    void createUser_withoutAuth_logsCreate_actorSystem_performedByNull() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("anonuser"); req.setEmail("anon@test.com");
        req.setFullName("Anon User"); req.setPassword("password");
        req.setRole(UserRole.DEVELOPER);

        String body = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long userId = objectMapper.readTree(body).get("id").asLong();

        JsonNode log = fetchLog("CREATE", "USER", userId);
        assertAuditLog(log, "CREATE", "USER", userId, "SYSTEM", null);
    }

    @Test
    void updateUser_withAuth_logsUpdate_actorUser() throws Exception {
        User dev = userRepository.findByUsername("dev").orElseThrow();
        UpdateUserRequest req = new UpdateUserRequest();
        req.setFullName("Updated Developer");

        mockMvc.perform(post("/users/update/" + dev.getId())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        JsonNode log = fetchLog("UPDATE", "USER", dev.getId());
        assertAuditLog(log, "UPDATE", "USER", dev.getId(), "USER", adminId);
    }

    @Test
    void deleteUser_withAuth_logsDelete_actorUser() throws Exception {
        User dev = userRepository.findByUsername("dev").orElseThrow();

        mockMvc.perform(delete("/users/" + dev.getId())
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        JsonNode log = fetchLog("DELETE", "USER", dev.getId());
        assertAuditLog(log, "DELETE", "USER", dev.getId(), "USER", adminId);
    }

    // ── project audit entries ─────────────────────────────────────────────────

    @Test
    void createProject_logsCreate_actorUser() throws Exception {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setName("New Project"); req.setDescription("desc"); req.setOwnerId(adminId);

        String body = mockMvc.perform(post("/projects")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long projectId = objectMapper.readTree(body).get("id").asLong();

        JsonNode log = fetchLog("CREATE", "PROJECT", projectId);
        assertAuditLog(log, "CREATE", "PROJECT", projectId, "USER", adminId);
    }

    @Test
    void updateProject_logsUpdate_actorUser() throws Exception {
        UpdateProjectRequest req = new UpdateProjectRequest();
        req.setName("Updated Project");

        mockMvc.perform(patch("/projects/" + project.getId())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        JsonNode log = fetchLog("UPDATE", "PROJECT", project.getId());
        assertAuditLog(log, "UPDATE", "PROJECT", project.getId(), "USER", adminId);
    }

    @Test
    void deleteProject_logsDelete_actorUser() throws Exception {
        mockMvc.perform(delete("/projects/" + project.getId())
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        JsonNode log = fetchLog("DELETE", "PROJECT", project.getId());
        assertAuditLog(log, "DELETE", "PROJECT", project.getId(), "USER", adminId);
    }

    @Test
    void restoreProject_logsRestore_actorUser() throws Exception {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setName("Restore Me"); req.setDescription("d"); req.setOwnerId(adminId);
        String body = mockMvc.perform(post("/projects")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long pid = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(delete("/projects/" + pid).header("Authorization", bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/projects/" + pid + "/restore").header("Authorization", bearer()))
                .andExpect(status().isOk());

        JsonNode log = fetchLog("RESTORE", "PROJECT", pid);
        assertAuditLog(log, "RESTORE", "PROJECT", pid, "USER", adminId);
    }

    // ── ticket audit entries ──────────────────────────────────────────────────

    @Test
    void createTicket_logsCreate_actorUser() throws Exception {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setTitle("New Ticket"); req.setStatus(TicketStatus.TODO);
        req.setPriority(TicketPriority.LOW); req.setType(TicketType.BUG);
        req.setProjectId(project.getId());
        req.setAssigneeId(adminId); // explicit assignee skips auto-assign

        String body = mockMvc.perform(post("/tickets")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long ticketId = objectMapper.readTree(body).get("id").asLong();

        JsonNode log = fetchLog("CREATE", "TICKET", ticketId);
        assertAuditLog(log, "CREATE", "TICKET", ticketId, "USER", adminId);
    }

    @Test
    void updateTicket_logsUpdate_actorUser() throws Exception {
        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setTitle("Updated Title");

        mockMvc.perform(put("/tickets/" + ticket.getId())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        JsonNode log = fetchLog("UPDATE", "TICKET", ticket.getId());
        assertAuditLog(log, "UPDATE", "TICKET", ticket.getId(), "USER", adminId);
    }

    @Test
    void deleteTicket_logsDelete_actorUser() throws Exception {
        mockMvc.perform(delete("/tickets/" + ticket.getId())
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        JsonNode log = fetchLog("DELETE", "TICKET", ticket.getId());
        assertAuditLog(log, "DELETE", "TICKET", ticket.getId(), "USER", adminId);
    }

    @Test
    void restoreTicket_logsRestore_actorUser() throws Exception {
        mockMvc.perform(delete("/tickets/" + ticket.getId())
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/tickets/" + ticket.getId() + "/restore")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        JsonNode log = fetchLog("RESTORE", "TICKET", ticket.getId());
        assertAuditLog(log, "RESTORE", "TICKET", ticket.getId(), "USER", adminId);
    }

    // ── comment audit entries ─────────────────────────────────────────────────

    @Test
    void createComment_logsCreate_actorUser() throws Exception {
        CreateCommentRequest req = new CreateCommentRequest();
        req.setContent("A comment"); req.setAuthorId(adminId);

        String body = mockMvc.perform(post("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long commentId = objectMapper.readTree(body).get("id").asLong();

        JsonNode log = fetchLog("CREATE", "COMMENT", commentId);
        assertAuditLog(log, "CREATE", "COMMENT", commentId, "USER", adminId);
    }

    @Test
    void updateComment_logsUpdate_actorUser() throws Exception {
        CreateCommentRequest create = new CreateCommentRequest();
        create.setContent("Original"); create.setAuthorId(adminId);
        String created = mockMvc.perform(post("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long commentId = objectMapper.readTree(created).get("id").asLong();

        UpdateCommentRequest update = new UpdateCommentRequest();
        update.setContent("Updated");
        mockMvc.perform(patch("/tickets/" + ticket.getId() + "/comments/" + commentId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        JsonNode log = fetchLog("UPDATE", "COMMENT", commentId);
        assertAuditLog(log, "UPDATE", "COMMENT", commentId, "USER", adminId);
    }

    @Test
    void deleteComment_logsDelete_actorUser() throws Exception {
        CreateCommentRequest create = new CreateCommentRequest();
        create.setContent("To delete"); create.setAuthorId(adminId);
        String created = mockMvc.perform(post("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long commentId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/tickets/" + ticket.getId() + "/comments/" + commentId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        JsonNode log = fetchLog("DELETE", "COMMENT", commentId);
        assertAuditLog(log, "DELETE", "COMMENT", commentId, "USER", adminId);
    }

    // ── auth audit entries ────────────────────────────────────────────────────

    @Test
    void login_logsLoginEvent_actorUserPerformedBySelf() throws Exception {
        // setUp's login() already produced the LOGIN audit log for admin
        JsonNode log = fetchLog("LOGIN", "AUTH", adminId);
        assertAuditLog(log, "LOGIN", "AUTH", adminId, "USER", adminId);
    }

    @Test
    void logout_logsLogoutEvent_actorUserPerformedBySelf() throws Exception {
        // Obtain a fresh token to log out (adminToken from setUp stays valid for fetchLog)
        String freshToken = login("admin", "pass");
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk());

        JsonNode log = fetchLog("LOGOUT", "AUTH", adminId);
        assertAuditLog(log, "LOGOUT", "AUTH", adminId, "USER", adminId);
    }

    // ── system actor ──────────────────────────────────────────────────────────

    @Test
    void createTicket_withAutoAssignment_logsAutoAssign_actorSystem_performedByNull() throws Exception {
        // No assigneeId → developer from setUp is auto-assigned
        CreateTicketRequest req = new CreateTicketRequest();
        req.setTitle("Auto Ticket"); req.setStatus(TicketStatus.TODO);
        req.setPriority(TicketPriority.LOW); req.setType(TicketType.BUG);
        req.setProjectId(project.getId());

        String body = mockMvc.perform(post("/tickets")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long ticketId = objectMapper.readTree(body).get("id").asLong();

        JsonNode log = fetchLog("AUTO_ASSIGN", "TICKET", ticketId);
        assertAuditLog(log, "AUTO_ASSIGN", "TICKET", ticketId, "SYSTEM", null);
    }

    // ── filter: by action ─────────────────────────────────────────────────────

    @Test
    void filter_byAction_returnsOnlyMatchingLogs() throws Exception {
        CreateUserRequest userReq = new CreateUserRequest();
        userReq.setUsername("filter1"); userReq.setEmail("filter1@test.com");
        userReq.setFullName("Filter One"); userReq.setPassword("password");
        userReq.setRole(UserRole.DEVELOPER);
        mockMvc.perform(post("/users").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReq)))
                .andExpect(status().isOk());

        UpdateProjectRequest projReq = new UpdateProjectRequest();
        projReq.setName("Updated For Filter");
        mockMvc.perform(patch("/projects/" + project.getId()).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projReq)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/audit-logs").param("action", "UPDATE")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data).isNotEmpty();
        for (JsonNode log : data) {
            assertThat(log.get("action").asText()).isEqualTo("UPDATE");
        }
    }

    // ── filter: by entityType ─────────────────────────────────────────────────

    @Test
    void filter_byEntityType_returnsOnlyMatchingLogs() throws Exception {
        CreateUserRequest userReq = new CreateUserRequest();
        userReq.setUsername("filter2"); userReq.setEmail("filter2@test.com");
        userReq.setFullName("Filter Two"); userReq.setPassword("password");
        userReq.setRole(UserRole.DEVELOPER);
        mockMvc.perform(post("/users").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReq)))
                .andExpect(status().isOk());

        CreateProjectRequest projReq = new CreateProjectRequest();
        projReq.setName("Filter Project"); projReq.setDescription("d"); projReq.setOwnerId(adminId);
        mockMvc.perform(post("/projects").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projReq)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/audit-logs").param("entityType", "PROJECT")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data).isNotEmpty();
        for (JsonNode log : data) {
            assertThat(log.get("entityType").asText()).isEqualTo("PROJECT");
        }
    }

    // ── filter: by entityId ───────────────────────────────────────────────────

    @Test
    void filter_byEntityId_isolatesLogsForThatEntity() throws Exception {
        CreateTicketRequest req1 = new CreateTicketRequest();
        req1.setTitle("T1"); req1.setStatus(TicketStatus.TODO);
        req1.setPriority(TicketPriority.LOW); req1.setType(TicketType.BUG);
        req1.setProjectId(project.getId()); req1.setAssigneeId(adminId);

        CreateTicketRequest req2 = new CreateTicketRequest();
        req2.setTitle("T2"); req2.setStatus(TicketStatus.TODO);
        req2.setPriority(TicketPriority.LOW); req2.setType(TicketType.BUG);
        req2.setProjectId(project.getId()); req2.setAssigneeId(adminId);

        String body1 = mockMvc.perform(post("/tickets").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String body2 = mockMvc.perform(post("/tickets").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        long ticketId1 = objectMapper.readTree(body1).get("id").asLong();
        long ticketId2 = objectMapper.readTree(body2).get("id").asLong();

        String filtered = mockMvc.perform(get("/audit-logs")
                        .param("entityId", String.valueOf(ticketId1))
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(filtered).get("data");
        assertThat(data).isNotEmpty();
        for (JsonNode log : data) {
            assertThat(log.get("entityId").asLong()).isEqualTo(ticketId1);
            assertThat(log.get("entityId").asLong()).isNotEqualTo(ticketId2);
        }
    }

    // ── filter: by actor ──────────────────────────────────────────────────────

    @Test
    void filter_byActorSystem_returnsOnlySystemLogs() throws Exception {
        // Create ticket without assigneeId → triggers AUTO_ASSIGN (SYSTEM actor)
        CreateTicketRequest req = new CreateTicketRequest();
        req.setTitle("System Ticket"); req.setStatus(TicketStatus.TODO);
        req.setPriority(TicketPriority.LOW); req.setType(TicketType.BUG);
        req.setProjectId(project.getId());
        mockMvc.perform(post("/tickets").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/audit-logs").param("actor", "SYSTEM")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data).isNotEmpty();
        for (JsonNode log : data) {
            assertThat(log.get("actor").asText()).isEqualTo("SYSTEM");
            assertThat(log.get("performedBy").isNull()).isTrue();
        }
    }

    // ── filter: by performedBy ────────────────────────────────────────────────

    @Test
    void filter_byPerformedBy_returnsOnlyLogsForThatUser() throws Exception {
        // Create a user without auth (performedBy=null) and one with auth (performedBy=adminId)
        CreateUserRequest noAuth = new CreateUserRequest();
        noAuth.setUsername("noauth"); noAuth.setEmail("noauth@test.com");
        noAuth.setFullName("No Auth"); noAuth.setPassword("password");
        noAuth.setRole(UserRole.DEVELOPER);
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noAuth)))
                .andExpect(status().isOk());

        CreateUserRequest withAuth = new CreateUserRequest();
        withAuth.setUsername("withauth"); withAuth.setEmail("withauth@test.com");
        withAuth.setFullName("With Auth"); withAuth.setPassword("password");
        withAuth.setRole(UserRole.DEVELOPER);
        mockMvc.perform(post("/users").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withAuth)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/audit-logs")
                        .param("performedBy", String.valueOf(adminId))
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data).isNotEmpty();
        for (JsonNode log : data) {
            assertThat(log.get("performedBy").asLong()).isEqualTo(adminId);
        }
    }

    // ── filter: combined params ───────────────────────────────────────────────

    @Test
    void filter_combinedParams_narrowsToSingleResult() throws Exception {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setName("Combined Filter"); req.setDescription("d"); req.setOwnerId(adminId);
        String body = mockMvc.perform(post("/projects").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long pid = objectMapper.readTree(body).get("id").asLong();

        UpdateProjectRequest updateReq = new UpdateProjectRequest();
        updateReq.setName("Combined Updated");
        mockMvc.perform(patch("/projects/" + pid).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // entityType=PROJECT + entityId=pid + action=CREATE → exactly one result
        String filtered = mockMvc.perform(get("/audit-logs")
                        .param("entityType", "PROJECT")
                        .param("entityId", String.valueOf(pid))
                        .param("action", "CREATE")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(filtered).get("data");
        assertThat(data).hasSize(1);
        assertAuditLog(data.get(0), "CREATE", "PROJECT", pid, "USER", adminId);
    }

    // ── pagination ────────────────────────────────────────────────────────────

    @Test
    void pagination_pageZeroSizeTwo_returnsNewestTwo() throws Exception {
        for (int i = 1; i <= 3; i++) {
            CreateProjectRequest req = new CreateProjectRequest();
            req.setName("Page Project " + i); req.setDescription("d"); req.setOwnerId(adminId);
            mockMvc.perform(post("/projects").header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
            Thread.sleep(20);
        }

        String body = mockMvc.perform(get("/audit-logs")
                        .param("action", "CREATE").param("entityType", "PROJECT")
                        .param("page", "0").param("pageSize", "2")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.get("data")).hasSize(2);
        assertThat(json.get("total").asLong()).isEqualTo(3);
        assertThat(json.get("page").asInt()).isEqualTo(0);
        assertThat(json.get("pageSize").asInt()).isEqualTo(2);
        assertThat(json.get("data").get(0).get("details").asText()).contains("Page Project 3");
        assertThat(json.get("data").get(1).get("details").asText()).contains("Page Project 2");
    }

    @Test
    void pagination_pageOne_returnsOldestLog() throws Exception {
        for (int i = 1; i <= 3; i++) {
            CreateProjectRequest req = new CreateProjectRequest();
            req.setName("Paged " + i); req.setDescription("d"); req.setOwnerId(adminId);
            mockMvc.perform(post("/projects").header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
            Thread.sleep(20);
        }

        String body = mockMvc.perform(get("/audit-logs")
                        .param("action", "CREATE").param("entityType", "PROJECT")
                        .param("page", "1").param("pageSize", "2")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.get("data")).hasSize(1);
        assertThat(json.get("total").asLong()).isEqualTo(3);
        assertThat(json.get("page").asInt()).isEqualTo(1);
        assertThat(json.get("data").get(0).get("details").asText()).contains("Paged 1");
    }

    // ── security ──────────────────────────────────────────────────────────────

    @Test
    void getAuditLogs_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    // ── response shape ────────────────────────────────────────────────────────

    @Test
    void response_hasDataTotalPagePageSizeFields() throws Exception {
        String body = mockMvc.perform(get("/audit-logs").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.has("data")).isTrue();
        assertThat(json.has("total")).isTrue();
        assertThat(json.has("page")).isTrue();
        assertThat(json.has("pageSize")).isTrue();
        assertThat(json.get("data").isArray()).isTrue();
        assertThat(json.get("total").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(json.get("page").asInt()).isEqualTo(0);
        assertThat(json.get("pageSize").asInt()).isEqualTo(20);
    }

    // ── timestamp ─────────────────────────────────────────────────────────────

    @Test
    void auditLog_timestamp_isRecentIsoString() throws Exception {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        CreateProjectRequest req = new CreateProjectRequest();
        req.setName("Timestamp Project"); req.setDescription("d"); req.setOwnerId(adminId);
        String created = mockMvc.perform(post("/projects").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long pid = objectMapper.readTree(created).get("id").asLong();

        JsonNode log = fetchLog("CREATE", "PROJECT", pid);

        assertThat(log.get("timestamp").isNull()).isFalse();
        LocalDateTime timestamp = LocalDateTime.parse(log.get("timestamp").asText());
        assertThat(timestamp).isAfterOrEqualTo(before);
        assertThat(timestamp).isBefore(LocalDateTime.now().plusSeconds(10));
    }
}
