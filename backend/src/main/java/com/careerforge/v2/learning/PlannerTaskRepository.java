package com.careerforge.v2.learning;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;

public interface PlannerTaskRepository extends JpaRepository<PlannerTask,Long> {
    List<PlannerTask> findByUserIdAndWeekStartOrderByDueDateAscIdAsc(Long userId,LocalDate weekStart);
    List<PlannerTask> findByUserIdAndDueDateBeforeAndStatusNotOrderByDueDateAscIdAsc(Long userId,LocalDate date,String status);
    Optional<PlannerTask> findByIdAndUserId(Long id,Long userId);
}
