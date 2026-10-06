package com.careerforge.v2.extras;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ResumeProfileRepository extends JpaRepository<ResumeProfile,Long> {
    Optional<ResumeProfile> findByUserId(Long userId);
}
