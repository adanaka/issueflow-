package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.ticket.dto.ImportResult;
import org.springframework.web.multipart.MultipartFile;

public interface TicketImportService {
    ImportResult importTickets(Long projectId, MultipartFile file);
}
