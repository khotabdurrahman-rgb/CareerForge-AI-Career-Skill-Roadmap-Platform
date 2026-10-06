package com.careerforge.repository;

import com.careerforge.model.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill,Long> {
    java.util.Optional<Skill> findByName(String name);
}
