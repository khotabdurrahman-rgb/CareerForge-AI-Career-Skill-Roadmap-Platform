package com.careerforge.v2.learning;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface QuizSessionRepository extends JpaRepository<QuizSession,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from QuizSession s where s.id = :id and s.user.id = :userId")
    Optional<QuizSession> lockOwned(@Param("id") String id,@Param("userId") Long userId);
}
