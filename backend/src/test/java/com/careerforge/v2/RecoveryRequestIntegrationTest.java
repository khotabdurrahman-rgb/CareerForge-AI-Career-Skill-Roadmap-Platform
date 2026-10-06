package com.careerforge.v2;

import com.careerforge.repository.UserRepository;
import com.careerforge.v2.security.MailDelivery;
import com.careerforge.v2.security.RecoveryService;
import com.careerforge.v2.security.RecoveryToken;
import com.careerforge.v2.security.RecoveryTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v2-recovery-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "careerforge.seed-demo=false", "careerforge.ai.api-key=", "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
class RecoveryRequestIntegrationTest extends V2RequestTestSupport {
    @MockitoBean MailDelivery mail;
    @Autowired RecoveryTokenRepository tokens;
    @Autowired UserRepository users;
    private final String remote = "qa-" + UUID.randomUUID();

    @BeforeEach
    void testSender() {
        when(mail.available()).thenReturn(false);
        when(mail.status()).thenReturn(Map.of("emailAvailable", true, "localEmail", true, "message", "Test sender enabled"));
    }

    private MockHttpServletRequestBuilder fromTest(MockHttpServletRequestBuilder request) {
        return request.with(servlet -> { servlet.setRemoteAddr(remote); return servlet; });
    }

    @Test
    void resetIsNeutralSingleUseAndRevokesEveryPriorSession() throws Exception {
        Account owner = register();
        clearInvocations(mail);
        when(mail.available()).thenReturn(true);
        var secondLogin = mvc.perform(body(post("/api/auth/login"), Map.of("email", owner.email(), "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        var otherSession = (org.springframework.mock.web.MockHttpSession) secondLogin.getRequest().getSession(false);
        JsonNode missing = ok(body(fromTest(post("/api/auth/forgot-password")), Map.of("email", "missing-" + UUID.randomUUID() + "@example.org")), null);
        JsonNode known = ok(body(fromTest(post("/api/auth/forgot-password")), Map.of("email", owner.email())), null);
        assertThat(known).isEqualTo(missing);
        noSecrets(known);
        String raw = deliveredToken(owner.email(), "reset-password");
        RecoveryToken stored = tokens.findAll().stream().filter(t -> t.tokenHash.equals(RecoveryService.digest(raw))).findFirst().orElseThrow();
        assertThat(stored.tokenHash).isNotEqualTo(raw);
        JsonNode reset = ok(body(fromTest(post("/api/auth/reset-password")), Map.of("token", raw, "password", "ChangedV2Password123!")), null);
        noSecrets(reset);
        mvc.perform(get("/api/me").session(owner.session())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").session(otherSession)).andExpect(status().isUnauthorized());
        mvc.perform(body(fromTest(post("/api/auth/login")), Map.of("email", owner.email(), "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
        noSecrets(ok(body(fromTest(post("/api/auth/login")), Map.of("email", owner.email(), "password", "ChangedV2Password123!")), null));
        mvc.perform(body(fromTest(post("/api/auth/reset-password")), Map.of("token", raw, "password", PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verificationMarksOnlyItsOwnerAndRejectsReplayAndWrongPurpose() throws Exception {
        Account owner = register();
        Account other = register();
        clearInvocations(mail);
        when(mail.available()).thenReturn(true);
        noSecrets(ok(body(fromTest(post("/api/auth/verification")), Map.of("email", owner.email())), null));
        String raw = deliveredToken(owner.email(), "verify-email");
        mvc.perform(body(fromTest(post("/api/auth/reset-password")), Map.of("token", raw, "password", "ChangedV2Password123!")))
                .andExpect(status().isBadRequest());
        noSecrets(ok(body(fromTest(post("/api/auth/verify-email")), Map.of("token", raw)), null));
        assertThat(ok(get("/api/me"), owner).path("emailVerified").asBoolean()).isTrue();
        assertThat(ok(get("/api/me"), other).path("emailVerified").asBoolean()).isFalse();
        mvc.perform(body(fromTest(post("/api/auth/verify-email")), Map.of("token", raw))).andExpect(status().isBadRequest());
    }

    @Test
    void expiredAndInvalidTokensCannotChangePassword() throws Exception {
        Account owner = register();
        String raw = UUID.randomUUID().toString();
        RecoveryToken expired = new RecoveryToken();
        expired.user = users.findById(owner.id()).orElseThrow();
        expired.tokenHash = RecoveryService.digest(raw);
        expired.purpose = "RESET";
        expired.createdAt = Instant.now().minusSeconds(3600);
        expired.expiresAt = Instant.now().minusSeconds(60);
        tokens.saveAndFlush(expired);
        for (String token : new String[]{raw, "invalid-token"})
            mvc.perform(body(fromTest(post("/api/auth/reset-password")), Map.of("token", token, "password", "ChangedV2Password123!")))
                    .andExpect(status().isBadRequest());
        ok(body(fromTest(post("/api/auth/login")), Map.of("email", owner.email(), "password", PASSWORD)), null);
        assertThat(tokens.findById(expired.id).orElseThrow().used).isFalse();
    }

    @Test
    void unavailableEmailStillReturnsNeutralResponseAndDoesNotCreateTokens() throws Exception {
        Account owner = register();
        clearInvocations(mail);
        when(mail.available()).thenReturn(false);
        JsonNode known = ok(body(fromTest(post("/api/auth/forgot-password")), Map.of("email", owner.email())), null);
        JsonNode unknown = ok(body(fromTest(post("/api/auth/forgot-password")), Map.of("email", "missing-" + UUID.randomUUID() + "@example.org")), null);
        assertThat(known).isEqualTo(unknown);
        assertThat(tokens.findByUserIdAndPurposeAndUsedFalse(owner.id(), "RESET")).isEmpty();
        verify(mail, never()).deliver(anyString(), anyString(), anyString());
    }

    private String deliveredToken(String email, String route) {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mail, timeout(5000)).deliver(eq(email), anyString(), text.capture());
        var matcher = Pattern.compile("/#" + route + "\\?token=([A-Za-z0-9_-]+)").matcher(text.getValue());
        assertThat(matcher.find()).as("Token delivered only through test sender").isTrue();
        return matcher.group(1);
    }
}
