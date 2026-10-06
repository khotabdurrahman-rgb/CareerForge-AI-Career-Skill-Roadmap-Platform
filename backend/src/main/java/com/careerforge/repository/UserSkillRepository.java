package com.careerforge.repository;

import com.careerforge.model.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSkillRepository extends JpaRepository<UserSkill,Long> {
    java.util.List<UserSkill> findByUserIdOrderByIdAsc(Long userId);
    java.util.Optional<UserSkill> findByUserIdAndSkillId(Long userId,Long skillId);
}
