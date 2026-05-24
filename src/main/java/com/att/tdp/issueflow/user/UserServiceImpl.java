package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.auditlog.AuditLogService;
import com.att.tdp.issueflow.common.SecurityUtils;
import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.user.dto.CreateUserRequest;
import com.att.tdp.issueflow.user.dto.UpdateUserRequest;
import com.att.tdp.issueflow.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return UserResponse.from(findOrThrow(id));
    }

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();

        User saved = userRepository.save(user);
        auditLogService.record(AuditAction.CREATE, AuditEntityType.USER, saved.getId(),
                securityUtils.getCurrentUserId(),
                "User '" + saved.getUsername() + "' created with role " + saved.getRole());
        return UserResponse.from(saved);
    }

    @Override
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findOrThrow(id);

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        User saved = userRepository.save(user);
        auditLogService.record(AuditAction.UPDATE, AuditEntityType.USER, saved.getId(),
                securityUtils.getCurrentUserId(),
                "User '" + saved.getUsername() + "' updated");
        return UserResponse.from(saved);
    }

    @Override
    public void deleteUser(Long id) {
        User user = findOrThrow(id);
        userRepository.deleteById(id);
        auditLogService.record(AuditAction.DELETE, AuditEntityType.USER, id,
                securityUtils.getCurrentUserId(),
                "User '" + user.getUsername() + "' deleted");
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
}
