package com.att.tdp.issueflow.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByUsernameIgnoreCase(String username);
    List<User> findAllByIdIn(Collection<Long> ids);

    @Query(value = """
            SELECT u.id AS userId, u.username AS username,
                   COUNT(t.id) AS openTicketCount
            FROM users u
            LEFT JOIN tickets t ON t.assignee_id = u.id
                AND t.project_id = :projectId
                AND t.status != 'DONE'
                AND t.deleted_at IS NULL
            WHERE u.role = 'DEVELOPER'
            GROUP BY u.id, u.username
            ORDER BY COUNT(t.id) ASC, u.id ASC
            """, nativeQuery = true)
    List<UserWorkloadView> findDevelopersForAssignment(@Param("projectId") Long projectId);

    @Query(value = """
            SELECT u.id AS userId, u.username AS username,
                   COUNT(t.id) AS openTicketCount
            FROM users u
            LEFT JOIN tickets t ON t.assignee_id = u.id
                AND t.project_id = :projectId
                AND t.status != 'DONE'
                AND t.deleted_at IS NULL
            WHERE u.role = 'DEVELOPER'
            GROUP BY u.id, u.username
            ORDER BY u.id ASC
            """, nativeQuery = true)
    List<UserWorkloadView> findDeveloperWorkloadByProject(@Param("projectId") Long projectId);
}
