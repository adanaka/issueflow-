package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.BaseIntegrationTest;
import com.att.tdp.issueflow.auth.dto.LoginRequest;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;
import com.att.tdp.issueflow.comment.dto.UpdateCommentRequest;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommentMentionTest extends BaseIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String token;
    private User mentioner;
    private User alice;
    private User bob;
    private Long ticketId;

    @BeforeEach
    void setUp() throws Exception {
        mentioner = userRepository.save(User.builder()
                .username("mentioner").email("mentioner@test.com").fullName("The Mentioner")
                .role(UserRole.ADMIN).password(passwordEncoder.encode("pass")).build());
        alice = userRepository.save(User.builder()
                .username("alice").email("alice@test.com").fullName("Alice Smith")
                .role(UserRole.DEVELOPER).build());
        bob = userRepository.save(User.builder()
                .username("bob").email("bob@test.com").fullName("Bob Jones")
                .role(UserRole.DEVELOPER).build());

        Project project = projectRepository.save(Project.builder()
                .name("Project").description("desc").owner(mentioner).build());

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .title("Ticket").priority(TicketPriority.MEDIUM)
                .status(TicketStatus.TODO).type(TicketType.BUG)
                .project(project).build());
        ticketId = ticket.getId();

        token = login("mentioner", "pass");
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

    private String bearer() { return "Bearer " + token; }

    private String postComment(String content) throws Exception {
        CreateCommentRequest req = new CreateCommentRequest();
        req.setContent(content);
        req.setAuthorId(mentioner.getId());
        return mockMvc.perform(post("/tickets/" + ticketId + "/comments")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Long postCommentId(String content) throws Exception {
        return objectMapper.readTree(postComment(content)).get("id").asLong();
    }

    private String patchComment(Long commentId, String content) throws Exception {
        UpdateCommentRequest req = new UpdateCommentRequest();
        req.setContent(content);
        return mockMvc.perform(patch("/tickets/" + ticketId + "/comments/" + commentId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ── mention parsing on create ─────────────────────────────────────────────

    @Test
    void create_withSingleMention_mentionedUsersContainsUser() throws Exception {
        String body = postComment("Hello @alice");

        assertThat(objectMapper.readTree(body).get("mentionedUsers")).hasSize(1);
        assertThat(objectMapper.readTree(body).get("mentionedUsers").get(0).get("username").asText())
                .isEqualTo("alice");
    }

    @Test
    void create_withUppercaseMention_matchesCaseInsensitively() throws Exception {
        String body = postComment("Hello @ALICE");

        assertThat(objectMapper.readTree(body).get("mentionedUsers")).hasSize(1);
        assertThat(objectMapper.readTree(body).get("mentionedUsers").get(0).get("username").asText())
                .isEqualTo("alice");
    }

    @Test
    void create_withUnknownMention_mentionedUsersIsEmpty() throws Exception {
        String body = postComment("Hello @ghost");

        assertThat(objectMapper.readTree(body).get("mentionedUsers")).isEmpty();
    }

    @Test
    void create_withTwoDistinctMentions_mentionedUsersContainsBoth() throws Exception {
        String body = postComment("Hey @alice and @bob");

        var mentioned = objectMapper.readTree(body).get("mentionedUsers");
        assertThat(mentioned).hasSize(2);
        assertThat(mentioned.findValuesAsText("username"))
                .containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void create_withDuplicateMention_mentionedUsersContainsOnce() throws Exception {
        String body = postComment("@alice @alice again @alice");

        assertThat(objectMapper.readTree(body).get("mentionedUsers")).hasSize(1);
        assertThat(objectMapper.readTree(body).get("mentionedUsers").get(0).get("username").asText())
                .isEqualTo("alice");
    }

    @Test
    void create_withNoMention_mentionedUsersIsEmptyArray() throws Exception {
        String body = postComment("Just a plain comment");

        var mentionedUsers = objectMapper.readTree(body).get("mentionedUsers");
        assertThat(mentionedUsers.isArray()).isTrue();
        assertThat(mentionedUsers).isEmpty();
    }

    // ── mention parsing on update ─────────────────────────────────────────────

    @Test
    void update_addMention_newUserAppearsInMentionedUsers() throws Exception {
        Long commentId = postCommentId("No mention yet");

        String body = patchComment(commentId, "Now I mention @alice");

        assertThat(objectMapper.readTree(body).get("mentionedUsers")).hasSize(1);
        assertThat(objectMapper.readTree(body).get("mentionedUsers").get(0).get("username").asText())
                .isEqualTo("alice");
    }

    @Test
    void update_removeMention_userRemovedFromMentionedUsers() throws Exception {
        Long commentId = postCommentId("Hello @alice");

        String body = patchComment(commentId, "Hello nobody");

        assertThat(objectMapper.readTree(body).get("mentionedUsers")).isEmpty();
    }

    @Test
    void update_keepSameMention_userStillPresent_rowNotDeletedAndReinserted() throws Exception {
        Long commentId = postCommentId("Hello @alice");

        // Record the ctid of the existing mention row — unchanged ctid means no delete+reinsert
        String ctidBefore = jdbcTemplate.queryForObject(
                "SELECT ctid::text FROM comment_mentions WHERE comment_id = ? AND user_id = ?",
                String.class, commentId, alice.getId());

        String body = patchComment(commentId, "Still mentioning @alice");

        String ctidAfter = jdbcTemplate.queryForObject(
                "SELECT ctid::text FROM comment_mentions WHERE comment_id = ? AND user_id = ?",
                String.class, commentId, alice.getId());

        // alice still present
        assertThat(objectMapper.readTree(body).get("mentionedUsers")).hasSize(1);
        assertThat(objectMapper.readTree(body).get("mentionedUsers").get(0).get("username").asText())
                .isEqualTo("alice");
        // and the row was not touched
        assertThat(ctidAfter).isEqualTo(ctidBefore);
    }

    @Test
    void update_replaceMention_oldRemovedNewAdded() throws Exception {
        Long commentId = postCommentId("Hello @alice");

        String body = patchComment(commentId, "Hello @bob");

        var usernames = objectMapper.readTree(body).get("mentionedUsers").findValuesAsText("username");
        assertThat(usernames).containsExactly("bob");
        assertThat(usernames).doesNotContain("alice");
    }

    // ── comment response shape ─────────────────────────────────────────────────

    @Test
    void getComments_mentionedUsersAlwaysPresent_emptyWhenNoMentions() throws Exception {
        postComment("Just plain text");

        mockMvc.perform(get("/tickets/" + ticketId + "/comments")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mentionedUsers").isArray())
                .andExpect(jsonPath("$[0].mentionedUsers", hasSize(0)));
    }

    @Test
    void getComments_mentionedUsersEntryHasIdUsernameFullName() throws Exception {
        postComment("Hey @alice");

        mockMvc.perform(get("/tickets/" + ticketId + "/comments")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mentionedUsers", hasSize(1)))
                .andExpect(jsonPath("$[0].mentionedUsers[0].id").value(alice.getId()))
                .andExpect(jsonPath("$[0].mentionedUsers[0].username").value("alice"))
                .andExpect(jsonPath("$[0].mentionedUsers[0].fullName").value("Alice Smith"));
    }

    // ── mentions endpoint ──────────────────────────────────────────────────────

    @Test
    void getMentions_returnsMentionedComments() throws Exception {
        postComment("Hey @alice, take a look");

        mockMvc.perform(get("/users/" + alice.getId() + "/mentions")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].content").value("Hey @alice, take a look"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getMentions_multipleComments_orderedNewestFirst() throws Exception {
        postComment("First @alice");
        Thread.sleep(50);
        postComment("Second @alice");
        Thread.sleep(50);
        postComment("Third @alice");

        mockMvc.perform(get("/users/" + alice.getId() + "/mentions")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].content").value("Third @alice"))
                .andExpect(jsonPath("$.content[1].content").value("Second @alice"))
                .andExpect(jsonPath("$.content[2].content").value("First @alice"));
    }

    @Test
    void getMentions_pagination_returnsCorrectSubset() throws Exception {
        postComment("First @alice");
        Thread.sleep(30);
        postComment("Second @alice");
        Thread.sleep(30);
        postComment("Third @alice");

        // page 0, size 2 — newest two
        mockMvc.perform(get("/users/" + alice.getId() + "/mentions")
                        .param("page", "0").param("pageSize", "2")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].content").value("Third @alice"))
                .andExpect(jsonPath("$.content[1].content").value("Second @alice"));

        // page 1, size 2 — oldest one
        mockMvc.perform(get("/users/" + alice.getId() + "/mentions")
                        .param("page", "1").param("pageSize", "2")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].content").value("First @alice"));
    }

    @Test
    void getMentions_userHasNoMentions_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/users/" + alice.getId() + "/mentions")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getMentions_nonExistentUser_returns404() throws Exception {
        mockMvc.perform(get("/users/9999/mentions")
                        .header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMentions_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/users/" + alice.getId() + "/mentions"))
                .andExpect(status().isUnauthorized());
    }

    // ── delete behavior ────────────────────────────────────────────────────────

    @Test
    void delete_commentWithMention_removedFromMentionsEndpoint() throws Exception {
        Long commentId = postCommentId("Hello @alice");

        mockMvc.perform(delete("/tickets/" + ticketId + "/comments/" + commentId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/" + alice.getId() + "/mentions")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ── mention diff: side effects on mentions endpoint ────────────────────────

    @Test
    void update_addMention_appearsInMentionsEndpoint() throws Exception {
        Long commentId = postCommentId("No mention");

        patchComment(commentId, "Now mentioning @alice");

        mockMvc.perform(get("/users/" + alice.getId() + "/mentions")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void update_removeMention_disappearsFromMentionsEndpoint() throws Exception {
        Long commentId = postCommentId("Hello @alice");

        patchComment(commentId, "Hello nobody");

        mockMvc.perform(get("/users/" + alice.getId() + "/mentions")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }
}
