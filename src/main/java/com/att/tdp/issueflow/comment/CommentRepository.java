package com.att.tdp.issueflow.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("SELECT DISTINCT c FROM Comment c JOIN FETCH c.author LEFT JOIN FETCH c.mentions cm LEFT JOIN FETCH cm.user WHERE c.ticket.id = :ticketId")
    List<Comment> findAllByTicketIdWithMentions(@Param("ticketId") Long ticketId);

    Optional<Comment> findByIdAndTicketId(Long id, Long ticketId);

    @Query(value = "SELECT c FROM Comment c JOIN FETCH c.author WHERE EXISTS (SELECT 1 FROM CommentMention cm WHERE cm.comment = c AND cm.id.userId = :userId)",
           countQuery = "SELECT COUNT(c) FROM Comment c WHERE EXISTS (SELECT 1 FROM CommentMention cm WHERE cm.comment = c AND cm.id.userId = :userId)")
    Page<Comment> findMentionedComments(@Param("userId") Long userId, Pageable pageable);
}
