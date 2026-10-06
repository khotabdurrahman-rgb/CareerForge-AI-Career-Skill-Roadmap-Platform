package com.careerforge.v2;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v2-no-ai-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "careerforge.seed-demo=false", "careerforge.ai.api-key=", "spring.mail.host=",
        "careerforge.mail.mode=smtp", "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
class MentorUnavailableRequestIntegrationTest extends V2RequestTestSupport {
    @Test
    void absentKeyReturns503WithoutPersistingInventedConversationOrSpendingQuota() throws Exception {
        Account owner = register();
        var before = ok(get("/api/mentor"), owner);
        assertThat(before.path("available").asBoolean()).isFalse();
        assertThat(before.path("message").asText()).isNotBlank();
        assertThat(before.path("messages").isEmpty()).isTrue();
        mvc.perform(body(post("/api/mentor").session(owner.session()), Map.of("message", "How do I learn Java?")))
                .andExpect(status().isServiceUnavailable());
        var after = ok(get("/api/mentor"), owner);
        assertThat(after.path("messages")).isEqualTo(before.path("messages"));
        assertThat(after.path("remaining")).isEqualTo(before.path("remaining"));
        ok(delete("/api/mentor/history"), owner);
        noSecrets(after);
    }

    @Test
    void mentorRequiresSessionAndConfiguredMailStatusContainsNoTokens() throws Exception {
        mvc.perform(get("/api/mentor")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/mentor/history")).andExpect(status().isUnauthorized());
        var config = ok(get("/api/auth/config"), null);
        assertThat(config.path("emailAvailable").asBoolean()).isFalse();
        assertThat(config.path("localEmail").asBoolean()).isFalse();
        assertThat(config.path("demoEnabled").asBoolean()).isFalse();
        noSecrets(config);
    }
}
