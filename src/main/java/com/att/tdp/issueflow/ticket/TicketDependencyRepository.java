package com.att.tdp.issueflow.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketDependencyRepository extends JpaRepository<TicketDependency, TicketDependencyId> {

    @Query("SELECT td.blockedBy FROM TicketDependency td " +
           "WHERE td.ticket.id = :ticketId " +
           "AND td.blockedBy.status != 'DONE'")
    List<Ticket> findUnresolvedBlockers(@Param("ticketId") Long ticketId);

    @Query("SELECT b FROM TicketDependency td " +
           "JOIN td.blockedBy b " +
           "JOIN FETCH b.project " +
           "WHERE td.ticket.id = :ticketId")
    List<Ticket> findBlockersByTicketId(@Param("ticketId") Long ticketId);

    @Modifying
    @Query("DELETE FROM TicketDependency td " +
           "WHERE td.ticket.id = :ticketId AND td.blockedBy.id = :blockedById")
    int deleteByTicketIdAndBlockedById(@Param("ticketId") Long ticketId,
                                       @Param("blockedById") Long blockedById);
}
