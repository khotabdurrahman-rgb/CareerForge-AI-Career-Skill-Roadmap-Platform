package com.careerforge.v2.learning;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt,Long> {
    List<AssessmentAttempt> findByUserIdOrderByCompletedAtDescIdDesc(Long userId);
}
