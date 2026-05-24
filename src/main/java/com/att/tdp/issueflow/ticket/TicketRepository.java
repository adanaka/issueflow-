package com.att.tdp.issueflow.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findAllByProjectIdAndDeletedAtIsNull(Long projectId);

    List<Ticket> findAllByProjectIdAndDeletedAtIsNotNull(Long projectId);

    Optional<Ticket> findByIdAndDeletedAtIsNull(Long id);

    Optional<Ticket> findByIdAndDeletedAtIsNotNull(Long id);

    // Finds tickets cascade-deleted together with their project (same timestamp)
    @Query("SELECT t FROM Ticket t WHERE t.project.id = :projectId AND t.deletedAt = :deletedAt")
    List<Ticket> findCascadeDeletedByProjectIdAndDeletedAt(@Param("projectId") Long projectId,
                                                           @Param("deletedAt") LocalDateTime deletedAt);

    // Loads active tickets with assignee in a single query (avoids N+1 on export)
    @Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.assignee WHERE t.project.id = :projectId AND t.deletedAt IS NULL")
    List<Ticket> findAllActiveByProjectIdWithAssignee(@Param("projectId") Long projectId);

    @Query("SELECT t FROM Ticket t WHERE t.dueDate IS NOT NULL " +
           "AND t.dueDate < :now " +
           "AND t.status != :doneStatus " +
           "AND t.deletedAt IS NULL")
    List<Ticket> findEligibleForEscalation(@Param("now") LocalDateTime now,
                                           @Param("doneStatus") TicketStatus doneStatus);
}
