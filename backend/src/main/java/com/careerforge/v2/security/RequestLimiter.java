package com.careerforge.v2.security;

import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RequestLimiter {
    private record Bucket(long window,int count) {}
    private final ConcurrentHashMap<String,Bucket> buckets=new ConcurrentHashMap<>();
    public void check(String key,int limit,long milliseconds) {
        long now=System.currentTimeMillis();
        if(buckets.size()>10000) buckets.entrySet().removeIf(entry->now-entry.getValue().window()>milliseconds);
        Bucket result=buckets.compute(key,(ignored,old)->old==null || now-old.window()>=milliseconds
                ? new Bucket(now,1) : new Bucket(old.window(),old.count()+1));
        if(result.count()>limit) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many requests. Please try again later.");
    }
}
