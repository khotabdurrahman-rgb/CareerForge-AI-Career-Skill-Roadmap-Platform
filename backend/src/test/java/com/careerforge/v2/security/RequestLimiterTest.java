package com.careerforge.v2.security;

import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class RequestLimiterTest {
    private static class TestClock extends Clock {
        long now;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
    }
    @Test void windowExpiresAndRejectionsDoNotExtendIt() {
        var clock=new TestClock(); var limiter=new RequestLimiter(clock,10);
        limiter.check("a",1,2000); clock.now=500;
        assertThatThrownBy(()->limiter.check("a",1,2000)).isInstanceOfSatisfying(ResponseStatusException.class,
                e->assertThat(e.getHeaders().getFirst("Retry-After")).isEqualTo("2"));
        clock.now=2000; assertThatCode(()->limiter.check("a",1,2000)).doesNotThrowAnyException();
    }
    @Test void bucketCleanupHonorsEachWindowsExpiryAndCapacityIsBounded() {
        var clock=new TestClock(); var limiter=new RequestLimiter(clock,2);
        limiter.check("long",1,10000); limiter.check("short",1,1000);
        assertThatThrownBy(()->limiter.check("third",1,1000)).isInstanceOf(ResponseStatusException.class);
        clock.now=1000; limiter.check("third",1,1000);
        assertThatThrownBy(()->limiter.check("long",1,10000)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void concurrentRequestsCannotExceedTheBudget() throws Exception {
        var limiter=new RequestLimiter(new TestClock(),10); var accepted=new AtomicInteger();
        var pool=Executors.newFixedThreadPool(8);
        try {
            var futures=new java.util.ArrayList<Future<?>>();
            for(int i=0;i<50;i++) futures.add(pool.submit(()->{
                try { limiter.check("shared",7,10000); accepted.incrementAndGet(); }
                catch(ResponseStatusException expected) { assertThat(expected.getStatusCode().value()).isEqualTo(429); }
            }));
            for(var future:futures) future.get(5,TimeUnit.SECONDS);
            assertThat(accepted.get()).isEqualTo(7);
        } finally { pool.shutdownNow(); }
    }
}
