package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;

import java.util.List;

public interface TicketService {
    TicketResponse createTicket(CreateTicketRequest request);
    TicketResponse getTicketById(Long id);
    List<TicketResponse> getTicketsByProject(Long projectId);
    List<TicketResponse> getDeletedTickets(Long projectId);
    TicketResponse updateTicket(Long id, UpdateTicketRequest request);
    void deleteTicket(Long id);
    void restoreTicket(Long id);
}
