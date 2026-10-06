package com.careerforge.v2.learning;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;

public interface PlannerGoalRepository extends JpaRepository<PlannerGoal,Long> {
    List<PlannerGoal> findByUserIdAndWeekStartOrderByIdAsc(Long userId,LocalDate weekStart);
    Optional<PlannerGoal> findByIdAndUserId(Long id,Long userId);
}
