package com.att.tdp.issueflow.project.dto;

import com.att.tdp.issueflow.user.UserWorkloadView;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkloadResponse {

    private Long userId;
    private String username;
    private Long openTicketCount;

    public static WorkloadResponse from(UserWorkloadView view) {
        return WorkloadResponse.builder()
                .userId(view.getUserId())
                .username(view.getUsername())
                .openTicketCount(view.getOpenTicketCount())
                .build();
    }
}
