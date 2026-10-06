package com.careerforge.repository;

import com.careerforge.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project,Long> {
    java.util.List<Project> findByUserIdOrderByIdDesc(Long userId);
}
