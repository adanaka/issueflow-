package com.att.tdp.issueflow.project.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProjectRequest {

    @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    private String name;

    @Size(min = 1, max = 500, message = "Description must be between 1 and 500 characters")
    private String description;
}
