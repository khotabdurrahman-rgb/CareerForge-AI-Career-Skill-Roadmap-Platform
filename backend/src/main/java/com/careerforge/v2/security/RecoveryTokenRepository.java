package com.careerforge.v2.security;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface RecoveryTokenRepository extends JpaRepository<RecoveryToken,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RecoveryToken> findByTokenHash(String hash);
    List<RecoveryToken> findByUserIdAndPurposeAndUsedFalse(Long userId,String purpose);
}
