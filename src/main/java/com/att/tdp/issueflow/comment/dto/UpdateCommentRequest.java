package com.att.tdp.issueflow.comment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateCommentRequest {

    @NotBlank(message = "Content is required")
    private String content;
}
