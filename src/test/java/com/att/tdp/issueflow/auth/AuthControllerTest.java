package com.att.tdp.issueflow.auth;

import com.att.tdp.issueflow.BaseIntegrationTest;
import com.att.tdp.issueflow.auth.dto.LoginRequest;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Long projectId;
    private Long ticketId;
    private String devToken;

    @BeforeEach
    void setUp() throws Exception {
        User admin = userRepository.save(User.builder()
                .username("admin").email("admin@test.com").fullName("Admin")
                .role(UserRole.ADMIN).password(passwordEncoder.encode("pass")).build());

        userRepository.save(User.builder()
                .username("dev").email("dev@test.com").fullName("Developer")
                .role(UserRole.DEVELOPER).password(passwordEncoder.encode("pass")).build());

        Project project = projectRepository.save(Project.builder()
                .name("Project").description("d").owner(admin).build());
        projectId = project.getId();

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .title("Ticket").priority(TicketPriority.MEDIUM)
                .status(TicketStatus.TODO).type(TicketType.BUG)
                .project(project).build());
        ticketId = ticket.getId();

        devToken = login("dev", "pass");
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

    // ── unauthenticated requests return 401 ───────────────────────────────────

    @Test
    void getTickets_noToken_returns401() throws Exception {
        mockMvc.perform(get("/tickets?projectId=" + projectId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProjects_noToken_returns401() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAuditLogs_noToken_returns401() throws Exception {
        mockMvc.perform(get("/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postTicket_noToken_returns401() throws Exception {
        mockMvc.perform(post("/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ── developer calling ADMIN-only endpoints returns 403 ────────────────────

    @Test
    void developer_getDeletedTickets_returns403() throws Exception {
        mockMvc.perform(get("/tickets/deleted?projectId=" + projectId)
                        .header("Authorization", bearer(devToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void developer_restoreTicket_returns403() throws Exception {
        mockMvc.perform(post("/tickets/" + ticketId + "/restore")
                        .header("Authorization", bearer(devToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void developer_getDeletedProjects_returns403() throws Exception {
        mockMvc.perform(get("/projects/deleted")
                        .header("Authorization", bearer(devToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void developer_restoreProject_returns403() throws Exception {
        mockMvc.perform(post("/projects/" + projectId + "/restore")
                        .header("Authorization", bearer(devToken)))
                .andExpect(status().isForbidden());
    }
}
