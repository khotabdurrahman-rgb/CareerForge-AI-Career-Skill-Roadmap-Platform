package com.careerforge.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v2-ai-http-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "careerforge.seed-demo=false",
        "careerforge.ai.api-key=local-test-key", "careerforge.ai.model=local-test-model", "careerforge.ai.daily-limit=2",
        "spring.jpa.open-in-view=false", "spring.mail.host=", "careerforge.mail.mode=smtp"
})
@AutoConfigureMockMvc
class MentorHttpIntegrationTest extends V2RequestTestSupport {
    private static final AtomicReference<String> payload = new AtomicReference<>();
    private static final AtomicReference<String> authorization = new AtomicReference<>();
    private static final AtomicReference<String> reply = new AtomicReference<>();
    private static final AtomicInteger providerStatus = new AtomicInteger(200);
    private static final AtomicInteger requests = new AtomicInteger();
    private static final HttpServer provider = startProvider();

    private static HttpServer startProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/responses", exchange -> {
                requests.incrementAndGet();
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] bytes = reply.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(providerStatus.get(), bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException error) { throw new ExceptionInInitializerError(error); }
    }

    @DynamicPropertySource
    static void localProvider(DynamicPropertyRegistry registry) {
        registry.add("careerforge.ai.url", () -> "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1/responses");
    }

    @AfterAll
    static void closeProvider() { provider.stop(0); }

    @Test
    void actualHttpClientSendsContextPersistsReplyAndEnforcesQuotaAfterHistoryClear() throws Exception {
        providerStatus.set(200);
        reply.set("{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Build a small Java API and test its ownership rules.\"}]}]}");
        Account owner = register();
        Account other = register();
        JsonNode skill = ok(get("/api/skills"), owner).get(0);
        ok(body(post("/api/skills"), Map.of("skillId", skill.path("id").asLong(), "level", "Intermediate")), owner);
        JsonNode first = ok(body(post("/api/mentor"), Map.of("message", "What should I build next?")), owner);
        assertThat(first.path("messages").size()).isEqualTo(2);
        assertThat(first.path("messages").get(0).path("role").asText()).isEqualTo("user");
        assertThat(first.path("messages").get(1).path("content").asText()).contains("small Java API");
        assertThat(first.path("remaining").asInt()).isEqualTo(1);
        assertThat(authorization.get()).isEqualTo("Bearer local-test-key");
        JsonNode sent = json.readTree(payload.get());
        assertThat(sent.path("model").asText()).isEqualTo("local-test-model");
        assertThat(sent.path("store").asBoolean()).isFalse();
        assertThat(sent.path("instructions").asText()).contains("selfReportedSkills", skill.path("name").asText());
        assertThat(sent.path("input").get(0).path("content").asText()).isEqualTo("What should I build next?");
        assertThat(ok(get("/api/mentor"), other).path("messages").isEmpty()).isTrue();
        ok(body(post("/api/mentor"), Map.of("message", "How should I test it?")), owner);
        assertThat(json.readTree(payload.get()).path("input").size()).isEqualTo(3);
        int before = requests.get();
        mvc.perform(body(post("/api/mentor").session(owner.session()), Map.of("message", "One more question")))
                .andExpect(status().isTooManyRequests());
        assertThat(requests.get()).isEqualTo(before);
        ok(delete("/api/mentor/history"), owner);
        JsonNode cleared = ok(get("/api/mentor"), owner);
        assertThat(cleared.path("messages").isEmpty()).isTrue();
        assertThat(cleared.path("remaining").asInt()).isZero();
        mvc.perform(body(post("/api/mentor").session(owner.session()), Map.of("message", "Clear cannot bypass quota")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void providerErrorsAndIncompleteRepliesPersistNoFakeMessages() throws Exception {
        for (int code : new int[]{429, 500, 200}) {
            Account owner = register();
            providerStatus.set(code);
            reply.set(code == 200 ? "{\"status\":\"incomplete\",\"output\":[]}" : "{\"error\":{\"message\":\"provider secret detail\"}}");
            var result = mvc.perform(body(post("/api/mentor").session(owner.session()), Map.of("message", "Advice please")))
                    .andExpect(status().isBadGateway()).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("provider secret detail", "local-test-key");
            JsonNode after = ok(get("/api/mentor"), owner);
            assertThat(after.path("messages").isEmpty()).isTrue();
            assertThat(after.path("remaining").asInt()).isEqualTo(1);
        }
    }
}
