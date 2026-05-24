package com.att.tdp.issueflow.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImportRowError {
    private int row;
    private String message;
}
