package com.att.tdp.issueflow.auth;

import com.att.tdp.issueflow.auth.dto.LoginRequest;
import com.att.tdp.issueflow.auth.dto.LoginResponse;
import com.att.tdp.issueflow.auth.dto.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        authService.logout(token);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> getMe() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(authService.getMe(username));
    }
}
