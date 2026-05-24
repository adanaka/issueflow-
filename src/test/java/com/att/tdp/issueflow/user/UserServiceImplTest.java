package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.auditlog.AuditLogService;
import com.att.tdp.issueflow.common.SecurityUtils;
import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.user.dto.CreateUserRequest;
import com.att.tdp.issueflow.user.dto.UpdateUserRequest;
import com.att.tdp.issueflow.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .username("jdoe")
                .email("jdoe@example.com")
                .fullName("John Doe")
                .role(UserRole.DEVELOPER)
                .build();
    }

    @Test
    void getAllUsers_returnsListOfResponses() {
        when(userRepository.findAll()).thenReturn(List.of(sampleUser));
        List<UserResponse> result = userService.getAllUsers();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("jdoe");
    }

    @Test
    void getUserById_existingId_returnsResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        UserResponse result = userService.getUserById(1L);
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("jdoe@example.com");
    }

    @Test
    void getUserById_missingId_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createUser_validRequest_savesAndReturns() {
        CreateUserRequest req = buildCreateRequest("newuser", "new@example.com");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        UserResponse result = userService.createUser(req);

        assertThat(result).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_duplicateUsername_throwsConflict() {
        CreateUserRequest req = buildCreateRequest("jdoe", "other@example.com");
        when(userRepository.existsByUsername("jdoe")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username already taken");
    }

    @Test
    void createUser_duplicateEmail_throwsConflict() {
        CreateUserRequest req = buildCreateRequest("newuser", "jdoe@example.com");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("jdoe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already registered");
    }

    @Test
    void updateUser_partialUpdate_onlyChangesProvidedFields() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserRequest req = new UpdateUserRequest();
        req.setFullName("Jane Doe");

        UserResponse result = userService.updateUser(1L, req);

        assertThat(result.getFullName()).isEqualTo("Jane Doe");
        assertThat(result.getRole()).isEqualTo(UserRole.DEVELOPER); // unchanged
    }

    @Test
    void deleteUser_existingId_deletesSuccessfully() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        userService.deleteUser(1L);
        verify(userRepository).deleteById(1L);
    }

    @Test
    void deleteUser_missingId_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.deleteUser(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private CreateUserRequest buildCreateRequest(String username, String email) {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername(username);
        req.setEmail(email);
        req.setFullName("Test User");
        req.setPassword("password123");
        req.setRole(UserRole.DEVELOPER);
        return req;
    }
}
