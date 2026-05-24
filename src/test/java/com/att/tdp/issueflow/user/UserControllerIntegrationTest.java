package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.BaseIntegrationTest;
import com.att.tdp.issueflow.user.dto.CreateUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createUser_validRequest_returns200() throws Exception {
        CreateUserRequest req = buildRequest("jdoe", "jdoe@example.com");

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.username").value("jdoe"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
    }

    @Test
    void createUser_missingUsername_returns400() throws Exception {
        CreateUserRequest req = buildRequest("jdoe", "jdoe@example.com");
        req.setUsername(null);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test
    void createUser_duplicateUsername_returns409() throws Exception {
        CreateUserRequest req = buildRequest("jdoe", "jdoe@example.com");

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Username already taken")));
    }

    @Test
    void getUserById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/users/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllUsers_emptyDb_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void deleteUser_existingUser_returns200() throws Exception {
        CreateUserRequest req = buildRequest("todelete", "todelete@example.com");
        String body = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(delete("/users/" + id))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/" + id))
                .andExpect(status().isNotFound());
    }

    private CreateUserRequest buildRequest(String username, String email) {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername(username);
        req.setEmail(email);
        req.setFullName("John Doe");
        req.setPassword("password123");
        req.setRole(UserRole.DEVELOPER);
        return req;
    }
}
