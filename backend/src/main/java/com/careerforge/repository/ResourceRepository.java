package com.careerforge.repository;

import com.careerforge.model.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRepository extends JpaRepository<Resource,Long> {
    void deleteBySkillId(Long skillId);
}
