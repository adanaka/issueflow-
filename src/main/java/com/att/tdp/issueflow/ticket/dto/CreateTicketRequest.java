package com.att.tdp.issueflow.ticket.dto;

import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateTicketRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private String description;

    @NotNull(message = "Status is required")
    private TicketStatus status;

    @NotNull(message = "Priority is required")
    private TicketPriority priority;

    @NotNull(message = "Type is required")
    private TicketType type;

    @NotNull(message = "Project ID is required")
    private Long projectId;

    private Long assigneeId;

    private LocalDateTime dueDate;
}
