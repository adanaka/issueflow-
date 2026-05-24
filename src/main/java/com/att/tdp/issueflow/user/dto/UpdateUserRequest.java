package com.att.tdp.issueflow.user.dto;

import com.att.tdp.issueflow.user.UserRole;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @Size(max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;

    private UserRole role;
}
