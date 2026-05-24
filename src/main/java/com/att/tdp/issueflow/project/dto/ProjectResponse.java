package com.att.tdp.issueflow.project.dto;

import com.att.tdp.issueflow.project.Project;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectResponse {

    private Long id;
    private String name;
    private String description;
    private Long ownerId;

    public static ProjectResponse from(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .ownerId(project.getOwner().getId())
                .build();
    }
}
