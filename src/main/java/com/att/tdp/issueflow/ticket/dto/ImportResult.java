package com.att.tdp.issueflow.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ImportResult {
    private int created;
    private int failed;
    private List<ImportRowError> errors;
}
