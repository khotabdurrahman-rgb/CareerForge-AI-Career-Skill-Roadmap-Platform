package com.careerforge.repository;

import com.careerforge.model.RoadmapStep;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapStepRepository extends JpaRepository<RoadmapStep,Long> {
    java.util.List<RoadmapStep> findByUserIdAndCareerIdOrderByPositionAsc(Long userId,Long careerId);
    void deleteByCareerId(Long careerId);
}
