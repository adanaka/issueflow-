package com.att.tdp.issueflow.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentMentionRepository extends JpaRepository<CommentMention, CommentMentionId> {

    List<CommentMention> findByIdCommentId(Long commentId);

    @Query("SELECT cm FROM CommentMention cm JOIN FETCH cm.user WHERE cm.id.commentId IN :commentIds")
    List<CommentMention> findByCommentIdIn(@Param("commentIds") List<Long> commentIds);
}
