package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.project.dto.CreateProjectRequest;
import com.att.tdp.issueflow.project.dto.ProjectResponse;
import com.att.tdp.issueflow.project.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.project.dto.WorkloadResponse;

import java.util.List;

public interface ProjectService {
    List<ProjectResponse> getAllProjects();
    List<ProjectResponse> getDeletedProjects();
    ProjectResponse getProjectById(Long id);
    ProjectResponse createProject(CreateProjectRequest request);
    ProjectResponse updateProject(Long id, UpdateProjectRequest request);
    void deleteProject(Long id);
    void restoreProject(Long id);
    List<WorkloadResponse> getWorkload(Long projectId);
}
