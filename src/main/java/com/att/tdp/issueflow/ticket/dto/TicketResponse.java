package com.att.tdp.issueflow.ticket.dto;

import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@Builder
public class TicketResponse {

    private Long id;
    private String title;
    private String description;
    private TicketStatus status;
    private TicketPriority priority;
    private TicketType type;
    private Long projectId;
    private Long assigneeId;
    private LocalDateTime dueDate;
    @JsonProperty("isOverdue")
    private boolean isOverdue;

    public static TicketResponse from(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .type(ticket.getType())
                .projectId(ticket.getProject().getId())
                .assigneeId(ticket.getAssignee() != null ? ticket.getAssignee().getId() : null)
                .dueDate(ticket.getDueDate())
                .isOverdue(ticket.isOverdue())
                .build();
    }
}
