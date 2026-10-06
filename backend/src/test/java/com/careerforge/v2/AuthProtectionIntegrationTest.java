package com.careerforge.v2;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:auth-protection-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "careerforge.seed-demo=false", "careerforge.ai.api-key=", "spring.mail.host=",
        "careerforge.mail.mode=smtp", "careerforge.auth.login-limit=2",
        "careerforge.auth.registration-limit=2"
})
@AutoConfigureMockMvc
class AuthProtectionIntegrationTest extends V2RequestTestSupport {
    @Autowired MeterRegistry metrics;
    @Autowired HealthContributorRegistry healthRegistry;

    @Test void wrongCredentialsAreLimitedAcrossSessionsAndEmailAddresses() throws Exception {
        for(int i=0;i<2;i++) mvc.perform(body(post("/api/auth/login").with(r->{r.setRemoteAddr("192.0.2.10");return r;}),
                Map.of("email","missing"+i+"@example.org","password",PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(body(post("/api/auth/login").with(r->{r.setRemoteAddr("192.0.2.10");return r;})
                .header("X-Forwarded-For","192.0.2.99"),Map.of("email","another@example.org","password",PASSWORD)))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."));
        mvc.perform(body(post("/api/auth/login").with(r->{r.setRemoteAddr("192.0.2.11");return r;}),
                Map.of("email","another@example.org","password",PASSWORD))).andExpect(status().isUnauthorized());
        assertThat(metrics.counter("careerforge.auth.rate_limited","operation","login").count()).isGreaterThanOrEqualTo(1);
    }

    @Test void malformedRegistrationsAreCountedBeforeValidationAndDoNotBlockLogin() throws Exception {
        for(int i=0;i<2;i++) mvc.perform(post("/api/auth/register").with(r->{r.setRemoteAddr("192.0.2.20");return r;})
                .contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest());
        mvc.perform(body(post("/api/auth/register").with(r->{r.setRemoteAddr("192.0.2.20");return r;}),
                Map.of("name","Rate QA","email",UUID.randomUUID()+"@example.org","password",PASSWORD)))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        mvc.perform(body(post("/api/auth/login").with(r->{r.setRemoteAddr("192.0.2.20");return r;}),
                Map.of("email","missing@example.org","password",PASSWORD))).andExpect(status().isUnauthorized());
    }

    @Test void healthIsPublicAndMinimalWhileOtherManagementEndpointsStayHidden() throws Exception {
        for(String path:new String[]{"/actuator/health","/actuator/health/readiness","/actuator/health/liveness"}) {
            var health=ok(get(path),null);
            assertThat(health.has("components")).isFalse();
            assertThat(health.has("details")).isFalse();
            if(!path.equals("/actuator/health")) assertThat(health.size()).isEqualTo(1);
            assertThat(health.path("status").asText()).isEqualTo("UP");
        }
        for(String path:new String[]{"/actuator","/actuator/env","/actuator/metrics","/actuator/heapdump"})
            mvc.perform(get(path)).andExpect(status().isNotFound());
        var counter=metrics.counter("careerforge.auth.rate_limited","operation","registration");
        assertThat(counter.getId().getTags()).allMatch(tag->tag.getKey().equals("operation"));
    }

    @Test void databaseFailureMarksReadinessDownWithoutLeakingDetailsOrFailingLiveness() throws Exception {
        var original=healthRegistry.unregisterContributor("db");
        try {
            healthRegistry.registerContributor("db",(HealthIndicator)()->Health.down().withDetail("connection","private-test-detail").build());
            mvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable())
                    .andExpect(content().json("{\"status\":\"DOWN\"}"));
            assertThat(ok(get("/actuator/health/liveness"),null).path("status").asText()).isEqualTo("UP");
        } finally {
            healthRegistry.unregisterContributor("db");
            if(original!=null) healthRegistry.registerContributor("db",original);
        }
    }
}
