package com.careerforge.v2.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage,Long> {
    List<ChatMessage> findByUserIdOrderByCreatedAtDescIdDesc(Long userId,Pageable pageable);
    void deleteByUserId(Long userId);
}
