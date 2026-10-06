package com.careerforge.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class V2RequestTestSupport {
    static final String PASSWORD = "V2Password123!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    record Account(long id, String email, MockHttpSession session) {}

    Account register() throws Exception {
        String email = "v2-" + UUID.randomUUID() + "@example.org";
        MvcResult result = mvc.perform(body(post("/api/auth/register"), Map.of(
                "name", "V2 QA Student", "email", email, "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        JsonNode user = json.readTree(result.getResponse().getContentAsByteArray());
        return new Account(user.path("id").asLong(), email,
                (MockHttpSession) result.getRequest().getSession(false));
    }

    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object value) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(value));
    }

    JsonNode ok(MockHttpServletRequestBuilder request, Account account) throws Exception {
        if (account != null) request.session(account.session());
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsByteArray());
    }

    static JsonNode find(JsonNode rows, long id) {
        for (JsonNode row : rows) if (row.path("id").asLong() == id) return row;
        throw new AssertionError("Missing record " + id + " in " + rows);
    }

    static void noSecrets(JsonNode value) {
        if (value.isObject()) {
            for (String key : new String[]{"password", "passwordHash", "sessionVersion", "token", "tokenHash"})
                assertThat(value.has(key)).as("No secret field %s", key).isFalse();
        }
        if (value.isContainerNode()) value.forEach(V2RequestTestSupport::noSecrets);
    }
}
