package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "comment_mentions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentMention {

    @EmbeddedId
    private CommentMentionId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("commentId")
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
