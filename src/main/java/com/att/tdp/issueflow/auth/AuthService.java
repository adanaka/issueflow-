package com.att.tdp.issueflow.auth;

import com.att.tdp.issueflow.auth.dto.LoginRequest;
import com.att.tdp.issueflow.auth.dto.LoginResponse;
import com.att.tdp.issueflow.auth.dto.MeResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    void logout(String token);
    MeResponse getMe(String username);
}
