package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.ImportResult;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketExportService ticketExportService;
    private final TicketImportService ticketImportService;

    @GetMapping
    public ResponseEntity<List<TicketResponse>> getTicketsByProject(@RequestParam Long projectId) {
        return ResponseEntity.ok(ticketService.getTicketsByProject(projectId));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportTickets(@RequestParam Long projectId) {
        String csv = ticketExportService.export(projectId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tickets-" + projectId + ".csv\"")
                .body(csv);
    }

    @PostMapping("/import")
    public ResponseEntity<ImportResult> importTickets(
            @RequestParam Long projectId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ticketImportService.importTickets(projectId, file));
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        return ResponseEntity.ok(ticketService.createTicket(request));
    }

    @GetMapping("/deleted")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TicketResponse>> getDeletedTickets(@RequestParam Long projectId) {
        return ResponseEntity.ok(ticketService.getDeletedTickets(projectId));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> getTicketById(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.getTicketById(ticketId));
    }

    @PutMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> updateTicket(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateTicketRequest request) {
        return ResponseEntity.ok(ticketService.updateTicket(ticketId, request));
    }

    @DeleteMapping("/{ticketId}")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long ticketId) {
        ticketService.deleteTicket(ticketId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{ticketId}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> restoreTicket(@PathVariable Long ticketId) {
        ticketService.restoreTicket(ticketId);
        return ResponseEntity.ok().build();
    }
}
