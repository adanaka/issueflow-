package com.att.tdp.issueflow.ticket;

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
public class TicketDependencyId implements Serializable {

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "blocked_by_id")
    private Long blockedById;
}
