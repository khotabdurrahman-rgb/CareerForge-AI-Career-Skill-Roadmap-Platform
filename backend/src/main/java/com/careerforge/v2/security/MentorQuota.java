package com.careerforge.v2.security;

import com.careerforge.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;

@Service
public class MentorQuota {
    private final MentorUsageRepository usage;
    private final UserRepository users;
    public MentorQuota(MentorUsageRepository usage,UserRepository users) { this.usage=usage; this.users=users; }
    public int used(Long userId) {
        return usage.findByUserIdAndUsageDate(userId,LocalDate.now(ZoneOffset.UTC)).map(row->row.requestCount).orElse(0);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void reserve(Long userId,int limit) {
        var user=users.findLockedById(userId).orElseThrow();
        LocalDate date=LocalDate.now(ZoneOffset.UTC);
        MentorUsage row=usage.findByUserIdAndUsageDate(userId,date).orElseGet(MentorUsage::new);
        if(row.requestCount>=limit) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Your daily mentor limit has been reached. It resets at midnight UTC.");
        row.user=user; row.usageDate=date; row.requestCount++; usage.save(row);
    }
}
