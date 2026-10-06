package com.careerforge.repository;

import com.careerforge.model.Career;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerRepository extends JpaRepository<Career,Long> {
    boolean existsByName(String name);
}
