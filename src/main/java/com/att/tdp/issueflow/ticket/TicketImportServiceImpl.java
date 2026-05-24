package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.auditlog.AuditLogService;
import com.att.tdp.issueflow.common.SecurityUtils;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.ImportResult;
import com.att.tdp.issueflow.ticket.dto.ImportRowError;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TicketImportServiceImpl implements TicketImportService {

    private final TicketService ticketService;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final SecurityUtils securityUtils;

    @Override
    public ImportResult importTickets(Long projectId, MultipartFile file) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        List<CSVRecord> records = parseFile(file);
        Map<Long, User> userCache = batchLoadAssignees(records);

        int created = 0;
        int failed = 0;
        List<ImportRowError> errors = new ArrayList<>();

        for (int i = 0; i < records.size(); i++) {
            int rowNumber = i + 1;
            try {
                CreateTicketRequest req = buildRequest(records.get(i), projectId, userCache);
                TicketResponse saved = ticketService.createTicket(req);
                auditLogService.record(AuditAction.CREATE, AuditEntityType.TICKET, saved.getId(),
                        securityUtils.getCurrentUserId(), "Ticket imported from CSV");
                created++;
            } catch (Exception e) {
                failed++;
                errors.add(new ImportRowError(rowNumber, e.getMessage()));
            }
        }

        return new ImportResult(created, failed, errors);
    }

    // --- private helpers ---

    @SuppressWarnings("deprecation")
    private List<CSVRecord> parseFile(MultipartFile file) {
        CSVFormat format = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .withIgnoreEmptyLines(true)
                .withTrim(true);
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, format)) {
            return parser.getRecords();
        } catch (IOException e) {
            throw new BadRequestException("Failed to parse CSV file: " + e.getMessage());
        }
    }

    private Map<Long, User> batchLoadAssignees(List<CSVRecord> records) {
        Set<Long> ids = new LinkedHashSet<>();
        for (CSVRecord record : records) {
            String raw = col(record, "assigneeId");
            if (raw != null && !raw.isBlank()) {
                try {
                    ids.add(Long.parseLong(raw));
                } catch (NumberFormatException ignored) {
                    // invalid IDs are caught per-row during validation
                }
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, User> map = new HashMap<>();
        userRepository.findAllByIdIn(ids).forEach(u -> map.put(u.getId(), u));
        return map;
    }

    private CreateTicketRequest buildRequest(CSVRecord record, Long projectId, Map<Long, User> userCache) {
        String title = col(record, "title");
        if (title == null || title.isBlank()) {
            throw new BadRequestException("Missing required field: title");
        }

        String statusStr = col(record, "status");
        if (statusStr == null || statusStr.isBlank()) {
            throw new BadRequestException("Missing required field: status");
        }
        TicketStatus status = parseEnum(TicketStatus.class, statusStr, "status");

        String priorityStr = col(record, "priority");
        if (priorityStr == null || priorityStr.isBlank()) {
            throw new BadRequestException("Missing required field: priority");
        }
        TicketPriority priority = parseEnum(TicketPriority.class, priorityStr, "priority");

        String typeStr = col(record, "type");
        if (typeStr == null || typeStr.isBlank()) {
            throw new BadRequestException("Missing required field: type");
        }
        TicketType type = parseEnum(TicketType.class, typeStr, "type");

        String descRaw = col(record, "description");
        String description = (descRaw == null || descRaw.isBlank()) ? null : descRaw;

        Long assigneeId = null;
        String assigneeRaw = col(record, "assigneeId");
        if (assigneeRaw != null && !assigneeRaw.isBlank()) {
            long parsedId;
            try {
                parsedId = Long.parseLong(assigneeRaw);
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid assigneeId: " + assigneeRaw);
            }
            if (!userCache.containsKey(parsedId)) {
                throw new ResourceNotFoundException("User not found with id: " + parsedId);
            }
            assigneeId = parsedId;
        }

        CreateTicketRequest req = new CreateTicketRequest();
        req.setTitle(title);
        req.setDescription(description);
        req.setStatus(status);
        req.setPriority(priority);
        req.setType(type);
        req.setProjectId(projectId);
        req.setAssigneeId(assigneeId);
        return req;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String fieldName) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid " + fieldName + " value: " + value);
        }
    }

    private static String col(CSVRecord record, String name) {
        return record.isMapped(name) ? record.get(name) : null;
    }
}
