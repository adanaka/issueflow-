package com.att.tdp.issueflow.ticket.dto;

import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DependencyResponse {

    private Long id;
    private String title;
    private TicketStatus status;
    private TicketPriority priority;
    private Long projectId;

    public static DependencyResponse from(Ticket ticket) {
        return DependencyResponse.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .projectId(ticket.getProject().getId())
                .build();
    }
}
