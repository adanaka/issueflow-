package com.att.tdp.issueflow.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CommentMentionId implements Serializable {

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "user_id")
    private Long userId;
}
