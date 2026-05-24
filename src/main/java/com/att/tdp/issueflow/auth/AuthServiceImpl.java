package com.att.tdp.issueflow.auth;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.auditlog.AuditLogService;
import com.att.tdp.issueflow.auth.dto.LoginRequest;
import com.att.tdp.issueflow.auth.dto.LoginResponse;
import com.att.tdp.issueflow.auth.dto.MeResponse;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid credentials"));

        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResourceNotFoundException("Invalid credentials");
        }

        String token = jwtService.generateToken(user);
        auditLogService.record(AuditAction.LOGIN, AuditEntityType.AUTH, user.getId(), user.getId(),
                "User '" + user.getUsername() + "' logged in");
        return new LoginResponse(token, "Bearer", 3600);
    }

    @Override
    public void logout(String token) {
        String jti = jwtService.extractJti(token);
        tokenBlacklist.add(jti, jwtService.extractExpiration(token).toInstant());

        String username = jwtService.extractUsername(token);
        userRepository.findByUsername(username).ifPresent(user ->
                auditLogService.record(AuditAction.LOGOUT, AuditEntityType.AUTH, user.getId(), user.getId(),
                        "User '" + username + "' logged out"));
    }

    @Override
    public MeResponse getMe(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return MeResponse.from(user);
    }
}
