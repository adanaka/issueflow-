package com.att.tdp.issueflow.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByDeletedAtIsNull();

    List<Project> findAllByDeletedAtIsNotNull();

    Optional<Project> findByIdAndDeletedAtIsNull(Long id);

    Optional<Project> findByIdAndDeletedAtIsNotNull(Long id);
}
