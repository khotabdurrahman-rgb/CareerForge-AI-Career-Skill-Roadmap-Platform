package com.careerforge.v2.security;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface MentorUsageRepository extends JpaRepository<MentorUsage,Long> {
    Optional<MentorUsage> findByUserIdAndUsageDate(Long userId,LocalDate date);
}
