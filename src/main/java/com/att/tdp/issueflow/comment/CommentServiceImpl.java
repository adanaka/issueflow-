package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.auditlog.AuditLogService;
import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;
import com.att.tdp.issueflow.comment.dto.UpdateCommentRequest;
import com.att.tdp.issueflow.common.SecurityUtils;
import com.att.tdp.issueflow.common.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentServiceImpl implements CommentService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9_]+)");

    private final CommentRepository commentRepository;
    private final CommentMentionRepository commentMentionRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final SecurityUtils securityUtils;

    @Override
    public CommentResponse createComment(Long ticketId, CreateCommentRequest request) {
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        User author = userRepository.findById(request.getAuthorId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getAuthorId()));

        Comment comment = Comment.builder()
                .content(request.getContent())
                .ticket(ticket)
                .author(author)
                .build();
        comment = commentRepository.save(comment);

        List<CommentMention> mentions = insertMentions(comment, parseMentionedUsernames(request.getContent()), Set.of());
        auditLogService.record(AuditAction.CREATE, AuditEntityType.COMMENT, comment.getId(),
                securityUtils.getCurrentUserId(),
                "Comment created on ticket " + ticketId);
        return CommentResponse.from(comment, mentions);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByTicket(Long ticketId) {
        ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        return commentRepository.findAllByTicketIdWithMentions(ticketId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    @Override
    public CommentResponse updateComment(Long ticketId, Long commentId, UpdateCommentRequest request) {
        Comment comment = commentRepository.findByIdAndTicketId(commentId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId));

        Set<Long> oldUserIds = commentMentionRepository.findByIdCommentId(commentId).stream()
                .map(cm -> cm.getId().getUserId())
                .collect(Collectors.toSet());

        comment.setContent(request.getContent());
        commentRepository.save(comment);

        List<CommentMention> finalMentions = diffAndSaveMentions(comment, oldUserIds, parseMentionedUsernames(request.getContent()));
        auditLogService.record(AuditAction.UPDATE, AuditEntityType.COMMENT, commentId,
                securityUtils.getCurrentUserId(),
                "Comment updated on ticket " + ticketId);
        return CommentResponse.from(comment, finalMentions);
    }

    @Override
    public void deleteComment(Long ticketId, Long commentId) {
        Comment comment = commentRepository.findByIdAndTicketId(commentId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId));
        commentRepository.delete(comment);
        auditLogService.record(AuditAction.DELETE, AuditEntityType.COMMENT, commentId,
                securityUtils.getCurrentUserId(),
                "Comment deleted from ticket " + ticketId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommentResponse> getMentionsForUser(Long userId, int page, int pageSize) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Comment> commentPage = commentRepository.findMentionedComments(userId, pageable);

        if (commentPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> commentIds = commentPage.getContent().stream().map(Comment::getId).toList();
        Map<Long, List<CommentMention>> mentionsByCommentId = commentMentionRepository
                .findByCommentIdIn(commentIds).stream()
                .collect(Collectors.groupingBy(cm -> cm.getId().getCommentId()));

        return commentPage.map(c -> CommentResponse.from(c, mentionsByCommentId.getOrDefault(c.getId(), List.of())));
    }

    // --- private helpers ---

    private Set<String> parseMentionedUsernames(String content) {
        Set<String> usernames = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            usernames.add(matcher.group(1).toLowerCase());
        }
        return usernames;
    }

    private List<CommentMention> insertMentions(Comment comment, Set<String> usernames, Set<Long> skipUserIds) {
        List<CommentMention> created = new ArrayList<>();
        for (String username : usernames) {
            userRepository.findByUsernameIgnoreCase(username).ifPresent(user -> {
                if (!skipUserIds.contains(user.getId())) {
                    CommentMentionId id = new CommentMentionId(comment.getId(), user.getId());
                    created.add(commentMentionRepository.save(
                            CommentMention.builder().id(id).comment(comment).user(user).build()));
                }
            });
        }
        return created;
    }

    private List<CommentMention> diffAndSaveMentions(Comment comment, Set<Long> oldUserIds, Set<String> newUsernames) {
        Map<Long, User> newUsersById = new LinkedHashMap<>();
        for (String username : newUsernames) {
            userRepository.findByUsernameIgnoreCase(username)
                    .ifPresent(u -> newUsersById.put(u.getId(), u));
        }
        Set<Long> newUserIds = newUsersById.keySet();

        Set<Long> toRemove = new HashSet<>(oldUserIds);
        toRemove.removeAll(newUserIds);
        for (Long userId : toRemove) {
            commentMentionRepository.deleteById(new CommentMentionId(comment.getId(), userId));
        }

        Set<Long> toAdd = new HashSet<>(newUserIds);
        toAdd.removeAll(oldUserIds);
        for (Long userId : toAdd) {
            CommentMentionId id = new CommentMentionId(comment.getId(), userId);
            commentMentionRepository.save(
                    CommentMention.builder().id(id).comment(comment).user(newUsersById.get(userId)).build());
        }

        return newUsersById.entrySet().stream()
                .map(e -> CommentMention.builder()
                        .id(new CommentMentionId(comment.getId(), e.getKey()))
                        .comment(comment)
                        .user(e.getValue())
                        .build())
                .toList();
    }
}
