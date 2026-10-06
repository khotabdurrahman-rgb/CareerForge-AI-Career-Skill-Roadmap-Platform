package com.careerforge.v2;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v2-extras-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "careerforge.seed-demo=false", "careerforge.ai.api-key=", "spring.jpa.open-in-view=false",
        "spring.mail.host=", "careerforge.mail.mode=smtp"
})
@AutoConfigureMockMvc
class ExtrasRequestIntegrationTest extends V2RequestTestSupport {
    @Test
    void resumePersistsPerOwnerAndPdfContainsOnlyIncludedSections() throws Exception {
        Account owner = register();
        Account other = register();
        JsonNode skill = ok(get("/api/skills"), owner).get(0);
        ok(body(post("/api/skills"), Map.of("skillId", skill.path("id").asLong(), "level", "Intermediate")), owner);
        ok(body(post("/api/projects"), Map.of("name", "Resume QA Project", "description", "Verified portfolio evidence",
                "technology", "Java", "status", "COMPLETED")), owner);
        Map<String, Object> profile = profile(true);
        JsonNode saved = ok(body(put("/api/resume"), profile), owner);
        assertThat(saved.path("profile")).isEqualTo(json.valueToTree(profile));
        JsonNode fetched = ok(get("/api/resume"), owner);
        assertThat(fetched).isEqualTo(saved);
        assertThat(fetched.path("user").path("email").asText()).isEqualTo(owner.email());
        assertThat(fetched.path("skills").get(0).path("skill").path("name").asText()).isEqualTo(skill.path("name").asText());
        assertThat(fetched.path("projects").get(0).path("name").asText()).isEqualTo("Resume QA Project");
        noSecrets(fetched);
        assertThat(ok(get("/api/resume"), other).path("profile").path("headline").asText()).isNotEqualTo("QA Engineer");
        String included = pdf(owner);
        assertThat(included).contains("V2 QA Student", owner.email(), "QA Engineer", "Reliable API testing",
                "Resume QA Project", "QA Education Marker", "QA Experience Marker", "QA Achievement Marker");
        ok(body(put("/api/resume"), profile(false)), owner);
        String omitted = pdf(owner);
        assertThat(omitted).contains("QA Engineer", owner.email()).doesNotContain("Resume QA Project",
                "QA Education Marker", "QA Experience Marker", "QA Achievement Marker");
        Map<String, Object> unsafe = profile(true);
        unsafe.put("website", "javascript:alert(1)");
        mvc.perform(body(put("/api/resume").session(owner.session()), unsafe)).andExpect(status().isBadRequest());
        assertThat(ok(get("/api/resume"), owner).path("profile").path("includeProjects").asBoolean()).isFalse();
    }

    private String pdf(Account account) throws Exception {
        byte[] bytes = mvc.perform(get("/api/resume/pdf").session(account.session())).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isPositive();
            return new PDFTextStripper().getText(document);
        }
    }

    private Map<String, Object> profile(boolean included) {
        Map<String, Object> fields = new LinkedHashMap<>(Map.of("headline", "QA Engineer", "summary", "Reliable API testing",
                "phone", "+91 9000000000", "location", "Mumbai", "website", "https://example.org/qa",
                "education", "QA Education Marker", "experience", "QA Experience Marker", "achievements", "QA Achievement Marker"));
        for (String field : List.of("includeSkills", "includeProjects", "includeEducation", "includeExperience", "includeAchievements"))
            fields.put(field, included);
        return fields;
    }

    @Test
    void comparisonUsesIndependentCareerGapsAndDoesNotChangeChosenCareer() throws Exception {
        Account owner = register();
        JsonNode careers = ok(get("/api/careers"), null);
        long left = careers.get(0).path("id").asLong();
        long right = careers.get(1).path("id").asLong();
        JsonNode before = ok(get("/api/me"), owner);
        JsonNode comparison = ok(get("/api/careers/compare?left=" + left + "&right=" + right), owner);
        assertThat(comparison.path("left").path("career").path("id").asLong()).isEqualTo(left);
        assertThat(comparison.path("right").path("career").path("id").asLong()).isEqualTo(right);
        assertThat(comparison.path("left").path("gap").path("readiness").asDouble()).isZero();
        assertThat(comparison.path("right").path("gap").path("readiness").asDouble()).isZero();
        assertThat(comparison.path("estimateExplanation").asText()).isNotBlank();
        assertThat(ok(get("/api/me"), owner).path("careerId")).isEqualTo(before.path("careerId"));
        mvc.perform(get("/api/careers/compare?left=" + left + "&right=9223372036854775807").session(owner.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void recommendationsAreOwnerScopedAndPortfolioAdditionIsIdempotent() throws Exception {
        Account owner = register();
        Account other = register();
        JsonNode recommendations = ok(get("/api/recommendations"), owner);
        assertThat(recommendations.isArray()).isTrue();
        assertThat(recommendations.isEmpty()).isFalse();
        String slug = recommendations.get(0).path("id").asText();
        String endpoint = "/api/recommendations/" + slug;
        assertThat(ok(post(endpoint + "/save"), owner).path("saved").asBoolean()).isTrue();
        assertThat(ok(post(endpoint + "/save"), owner).path("saved").asBoolean()).isTrue();
        for (JsonNode row : ok(get("/api/recommendations"), other))
            if (row.path("id").asText().equals(slug)) assertThat(row.path("saved").asBoolean()).isFalse();
        JsonNode first = ok(post(endpoint + "/portfolio"), owner);
        JsonNode second = ok(post(endpoint + "/portfolio"), owner);
        assertThat(second.path("id")).isEqualTo(first.path("id"));
        long matching = 0;
        for (JsonNode project : ok(get("/api/projects"), owner))
            if (project.path("id").equals(first.path("id"))) matching++;
        assertThat(matching).isEqualTo(1);
        assertThat(ok(delete(endpoint + "/save"), owner).path("saved").asBoolean()).isFalse();
        mvc.perform(post("/api/recommendations/missing-qa-recommendation/portfolio").session(owner.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void progressReadsDoNotManufactureEventsAndWritesRecordActualActivity() throws Exception {
        Account owner = register();
        JsonNode baseline = ok(get("/api/progress"), owner);
        for (String path : List.of("/api/dashboard", "/api/roadmap", "/api/resume", "/api/recommendations", "/api/progress"))
            ok(get(path), owner);
        JsonNode reread = ok(get("/api/progress"), owner);
        assertThat(reread.path("events")).isEqualTo(baseline.path("events"));
        assertThat(reread.path("trend").isArray()).isTrue();
        assertThat(reread.path("currentTimezone").asText()).isEqualTo("Asia/Kolkata");
        assertThat(reread.path("streakDefinition").asText()).isNotBlank();
        JsonNode step = ok(get("/api/roadmap"), owner).get(0);
        ok(body(put("/api/roadmap/" + step.path("id").asLong()), Map.of("status", "COMPLETED")), owner);
        JsonNode after = ok(get("/api/progress"), owner);
        assertThat(after.path("events").size()).isGreaterThan(baseline.path("events").size());
        assertThat(after.path("streak").asInt()).isPositive();
        assertThat(after.path("trend").isEmpty()).isFalse();
        assertThat(ok(get("/api/progress"), owner).path("events")).isEqualTo(after.path("events"));
    }

    @Test
    void extrasRequireAuthentication() throws Exception {
        for (String path : List.of("/api/resume", "/api/resume/pdf", "/api/recommendations", "/api/progress", "/api/careers/compare?left=1&right=2"))
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
}
