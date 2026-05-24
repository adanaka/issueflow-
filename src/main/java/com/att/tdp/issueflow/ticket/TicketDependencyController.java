package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.ticket.dto.AddDependencyRequest;
import com.att.tdp.issueflow.ticket.dto.DependencyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets/{ticketId}/dependencies")
@RequiredArgsConstructor
public class TicketDependencyController {

    private final TicketDependencyService dependencyService;

    @PostMapping
    public ResponseEntity<Void> addDependency(
            @PathVariable Long ticketId,
            @Valid @RequestBody AddDependencyRequest request) {
        dependencyService.addDependency(ticketId, request.getBlockedBy());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<DependencyResponse>> getDependencies(@PathVariable Long ticketId) {
        return ResponseEntity.ok(dependencyService.getDependencies(ticketId));
    }

    @DeleteMapping("/{blockerId}")
    public ResponseEntity<Void> removeDependency(
            @PathVariable Long ticketId,
            @PathVariable Long blockerId) {
        dependencyService.removeDependency(ticketId, blockerId);
        return ResponseEntity.ok().build();
    }
}
