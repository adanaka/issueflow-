package com.att.tdp.issueflow.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCommentRequest {

    @NotBlank(message = "Content is required")
    private String content;

    @NotNull(message = "Author ID is required")
    private Long authorId;
}
