package com.careerforge.v2.extras;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SavedRecommendationRepository extends JpaRepository<SavedRecommendation,Long> {
    Optional<SavedRecommendation> findByUserIdAndRecommendationId(Long userId,String recommendationId);
    List<SavedRecommendation> findByUserId(Long userId);
}
