package com.careerforge.v2.extras;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProgressEventRepository extends JpaRepository<ProgressEvent,Long> {
    List<ProgressEvent> findTop500ByUserIdOrderByOccurredAtDescIdDesc(Long userId);
}
