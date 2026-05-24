package com.att.tdp.issueflow.comment.dto;

import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.comment.CommentMention;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CommentResponse {

    private Long id;
    private String content;
    private Long ticketId;
    private Long authorId;
    private String authorUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MentionedUser> mentionedUsers;

    @Data
    @Builder
    public static class MentionedUser {
        private Long id;
        private String username;
        private String fullName;
    }

    public static CommentResponse from(Comment comment) {
        return from(comment, comment.getMentions());
    }

    public static CommentResponse from(Comment comment, List<CommentMention> mentions) {
        return CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .ticketId(comment.getTicket().getId())
                .authorId(comment.getAuthor().getId())
                .authorUsername(comment.getAuthor().getUsername())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .mentionedUsers(mentions.stream()
                        .map(cm -> MentionedUser.builder()
                                .id(cm.getUser().getId())
                                .username(cm.getUser().getUsername())
                                .fullName(cm.getUser().getFullName())
                                .build())
                        .toList())
                .build();
    }
}
