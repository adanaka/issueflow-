package com.att.tdp.issueflow.ticket.dto;

import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateTicketRequest {

    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    private String title;

    private String description;

    private TicketStatus status;

    private TicketPriority priority;

    private Long assigneeId;

    private LocalDateTime dueDate;
}
