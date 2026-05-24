package com.att.tdp.issueflow.ticket;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ticket_dependencies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketDependency {

    @EmbeddedId
    private TicketDependencyId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("ticketId")
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("blockedById")
    @JoinColumn(name = "blocked_by_id", nullable = false)
    private Ticket blockedBy;
}
