package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketExportServiceImpl implements TicketExportService {

    private static final String[] HEADERS =
            {"id", "title", "description", "status", "priority", "type", "assigneeId"};

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional(readOnly = true)
    public String export(Long projectId) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        List<Ticket> tickets = ticketRepository.findAllActiveByProjectIdWithAssignee(projectId);

        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT)) {
            printer.printRecord((Object[]) HEADERS);
            for (Ticket t : tickets) {
                printer.printRecord(
                        t.getId(),
                        t.getTitle(),
                        t.getDescription() != null ? t.getDescription() : "",
                        t.getStatus().name(),
                        t.getPriority().name(),
                        t.getType().name(),
                        t.getAssignee() != null ? t.getAssignee().getId() : ""
                );
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV", e);
        }

        return sw.toString();
    }
}
