package com.att.tdp.issueflow.auditlog.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuditLogPageResponse {
    private List<AuditLogResponse> data;
    private long total;
    private int page;
    private int pageSize;
}
