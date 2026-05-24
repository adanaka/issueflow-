package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.ticket.dto.DependencyResponse;

import java.util.List;

public interface TicketDependencyService {
    void addDependency(Long ticketId, Long blockedById);
    List<DependencyResponse> getDependencies(Long ticketId);
    void removeDependency(Long ticketId, Long blockedById);
}
