package com.att.tdp.issueflow.user.dto;

import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String fullName;
    private UserRole role;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }
}
