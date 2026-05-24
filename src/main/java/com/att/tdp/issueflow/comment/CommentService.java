package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;
import com.att.tdp.issueflow.comment.dto.UpdateCommentRequest;
import org.springframework.data.domain.Page;

import java.util.List;

public interface CommentService {
    CommentResponse createComment(Long ticketId, CreateCommentRequest request);
    List<CommentResponse> getCommentsByTicket(Long ticketId);
    CommentResponse updateComment(Long ticketId, Long commentId, UpdateCommentRequest request);
    void deleteComment(Long ticketId, Long commentId);
    Page<CommentResponse> getMentionsForUser(Long userId, int page, int pageSize);
}
