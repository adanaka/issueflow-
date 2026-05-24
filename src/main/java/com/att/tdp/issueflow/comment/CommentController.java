package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;
import com.att.tdp.issueflow.comment.dto.UpdateCommentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tickets/{ticketId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.ok(commentService.createComment(ticketId, request));
    }

    @GetMapping
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long ticketId) {
        return ResponseEntity.ok(commentService.getCommentsByTicket(ticketId));
    }

    @PatchMapping("/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable Long ticketId,
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request) {
        return ResponseEntity.ok(commentService.updateComment(ticketId, commentId, request));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long ticketId,
            @PathVariable Long commentId) {
        commentService.deleteComment(ticketId, commentId);
        return ResponseEntity.ok().build();
    }
}
