package com.careerforge.v2.security;

import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;

@Component
public class RequestLimiter {
    private record Bucket(long expiresAt,int count) {}
    private final Map<String,Bucket> buckets=new HashMap<>();
    private final Clock clock;
    private final int maxKeys;
    public RequestLimiter() { this(Clock.systemUTC(),10000); }
    RequestLimiter(Clock clock,int maxKeys) { this.clock=clock; this.maxKeys=maxKeys; }
    public synchronized void check(String key,int limit,long milliseconds) {
        if(limit<1 || milliseconds<1) throw new IllegalArgumentException("Rate limits must be positive");
        long now=clock.millis();
        Bucket old=buckets.get(key);
        if(old==null || old.expiresAt()<=now) {
            if(old==null && buckets.size()>=maxKeys) {
                buckets.entrySet().removeIf(entry->entry.getValue().expiresAt()<=now);
                if(buckets.size()>=maxKeys) throw limited(60);
            }
            buckets.put(key,new Bucket(now+milliseconds,1));
        } else {
            if(old.count()>=limit) throw limited((old.expiresAt()-now+999)/1000);
            buckets.put(key,new Bucket(old.expiresAt(),old.count()+1));
        }
    }
    private ResponseStatusException limited(long seconds) {
        return new RateLimited(seconds);
    }
    private static class RateLimited extends ResponseStatusException {
        private final HttpHeaders headers=new HttpHeaders();
        RateLimited(long seconds) {
            super(HttpStatus.TOO_MANY_REQUESTS,"Too many requests. Please try again later.");
            headers.set(HttpHeaders.RETRY_AFTER,Long.toString(Math.max(1,seconds)));
        }
        @Override public HttpHeaders getHeaders() { return headers; }
    }
}
