package com.careerforge.qa;

import com.careerforge.model.User;
import com.careerforge.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:qa;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "careerforge.seed-demo=false"
})
@AutoConfigureMockMvc
@Transactional
class WorkspaceApiIntegrationTest {
    private static final String PASSWORD = "QaPassword123!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;

    private record Account(long id, String email, long careerId, MockHttpSession session) {}

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void separateRequestTransactionsKeepCareerIdentifiersAndSkillChangesValid() throws Exception {
        Account student=register();
        long skillId=requiredSkills(student).get(0);
        JsonNode added=ok(body(post("/api/skills"),skill(skillId,"Beginner")),student.session());
        ok(get("/api/dashboard"),student.session());
        ok(delete("/api/skills/"+added.path("id").asLong()),student.session());
        JsonNode fresh=ok(get("/api/dashboard"),student.session());
        assertThat(fresh.path("skills").size()).isZero();
        assertThat(fresh.path("roadmap").get(0).path("careerId").asLong()).isEqualTo(student.careerId());
        long differentCareer=ok(get("/api/careers"),null).get(1).path("id").asLong();
        ok(body(put("/api/profile"),Map.of("name","QA Student","careerId",differentCareer)),student.session());
        JsonNode switched=ok(get("/api/dashboard"),student.session());
        assertThat(switched.path("career").path("id").asLong()).isEqualTo(differentCareer);
        assertThat(switched.path("roadmap").get(0).path("careerId").asLong()).isEqualTo(differentCareer);
    }

    @Test
    void registrationLoginAndLogoutRotateSessionsAndNeverExposePasswords() throws Exception {
        String email = uniqueEmail();
        MockHttpSession anonymous = new MockHttpSession();
        MvcResult registration = mvc.perform(body(post("/api/auth/register").session(anonymous),
                        Map.of("name", "  QA Student  ", "email", email.toUpperCase(Locale.ROOT),
                                "password", PASSWORD, "role", "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("QA Student"))
                .andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.role").value("STUDENT"))
                .andReturn();
        assertThat(anonymous.isInvalid()).isTrue();
        MockHttpSession registered = (MockHttpSession) registration.getRequest().getSession(false);
        assertThat(registered).isNotNull();
        assertThat(registered.getId()).isNotEqualTo(anonymous.getId());
        assertNoPassword(tree(registration));
        User stored = users.findByEmail(email).orElseThrow();
        assertThat(stored.passwordHash).isNotEqualTo(PASSWORD);
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, stored.passwordHash)).isTrue();

        mvc.perform(body(post("/api/auth/register"), Map.of("name", "Duplicate", "email", email,
                        "password", PASSWORD))).andExpect(status().isConflict());
        mvc.perform(body(post("/api/auth/login"), Map.of("email", email, "password", "wrong-password")))
                .andExpect(status().isUnauthorized());
        mvc.perform(body(post("/api/auth/login"), Map.of("email", uniqueEmail(), "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());

        MvcResult login = mvc.perform(body(post("/api/auth/login").session(registered),
                        Map.of("email", email.toUpperCase(Locale.ROOT), "password", PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(stored.id)).andReturn();
        assertThat(registered.isInvalid()).isTrue();
        MockHttpSession signedIn = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(signedIn.getId()).isNotEqualTo(registered.getId());
        assertNoPassword(tree(login));
        assertNoPassword(ok(get("/api/me"), signedIn));
        assertNoPassword(ok(get("/api/dashboard"), signedIn));
        assertNoPassword(ok(body(put("/api/profile"), Map.of("name", "Updated QA", "careerId", stored.careerId)), signedIn));
        mvc.perform(post("/api/auth/logout").session(signedIn)).andExpect(status().isOk());
        assertThat(signedIn.isInvalid()).isTrue();
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isOk());
    }

    @Test
    void protectedWorkspaceRoutesRequireAuthentication() throws Exception {
        for (String path : List.of("/api/me", "/api/dashboard", "/api/skills", "/api/roadmap",
                "/api/projects", "/api/resources", "/api/admin/users")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(body(post("/api/projects"), project("Anonymous"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/careers")).andExpect(status().isOk());
    }

    @Test
    void anotherStudentCannotReadOrMutateOwnerWorkspaceOrRecords() throws Exception {
        Account owner = register();
        Account intruder = register();
        long skillId = requiredSkills(owner).get(0);
        long learnedId = ok(body(post("/api/skills"), skill(skillId, "Intermediate")), owner.session()).path("id").asLong();
        long projectId = ok(body(post("/api/projects"), project("Owner project")), owner.session()).path("id").asLong();
        long stepId = ok(get("/api/roadmap"), owner.session()).get(0).path("id").asLong();

        for (String suffix : List.of("/skill-gap/" + owner.careerId(), "/roadmap", "/projects")) {
            mvc.perform(get("/api/users/" + owner.id() + suffix).session(intruder.session()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(body(post("/api/users/" + owner.id() + "/skills").session(intruder.session()),
                skill(skillId, "Advanced"))).andExpect(status().isForbidden());
        mvc.perform(body(post("/api/users/" + owner.id() + "/projects").session(intruder.session()),
                project("Intruder"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/skills/" + learnedId).session(intruder.session())).andExpect(status().isForbidden());
        mvc.perform(body(put("/api/roadmap/" + stepId).session(intruder.session()), Map.of("status", "IN_PROGRESS")))
                .andExpect(status().isForbidden());
        mvc.perform(body(put("/api/projects/" + projectId).session(intruder.session()), project("Hijacked")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/projects/" + projectId).session(intruder.session())).andExpect(status().isForbidden());

        JsonNode ownerDashboard = ok(get("/api/dashboard"), owner.session());
        assertThat(ownerDashboard.path("skills").get(0).path("level").asText()).isEqualTo("Intermediate");
        assertThat(ownerDashboard.path("projects").get(0).path("name").asText()).isEqualTo("Owner project");
        assertThat(ownerDashboard.path("roadmap").get(0).path("status").asText()).isEqualTo("COMPLETED");
        JsonNode intruderDashboard = ok(get("/api/dashboard"), intruder.session());
        assertThat(intruderDashboard.path("skills").size()).isZero();
        assertThat(intruderDashboard.path("projects").size()).isZero();
        assertThat(ok(get("/api/users/" + owner.id() + "/projects"), owner.session()).size()).isEqualTo(1);
    }

    @Test
    void studentProjectCrudPersistsAndDeletionRemovesIt() throws Exception {
        Account owner = register();
        long id = ok(body(post("/api/users/" + owner.id() + "/projects"), project("Initial")), owner.session()).path("id").asLong();
        Map<String, Object> edit = Map.of("name", "Final", "description", "QA description", "technology", "Java",
                "githubUrl", "https://github.com/example/qa", "status", "COMPLETED");
        JsonNode changed = ok(body(put("/api/projects/" + id), edit), owner.session());
        assertThat(changed.path("id").asLong()).isEqualTo(id);
        assertThat(changed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(ok(get("/api/projects"), owner.session()).get(0).path("name").asText()).isEqualTo("Final");
        ok(delete("/api/projects/" + id), owner.session());
        assertThat(ok(get("/api/projects"), owner.session()).size()).isZero();
        mvc.perform(body(put("/api/projects/" + id).session(owner.session()), edit)).andExpect(status().isNotFound());
    }

    @Test
    void adminAuthorizationAppliesToEveryCrudRouteAndAdminCanPersistChanges() throws Exception {
        Account student = register();
        Account admin = admin();
        long skillId = requiredSkills(student).get(0);
        Map<String, Object> career = Map.of("name", "QA " + UUID.randomUUID(), "description", "QA career", "skillIds", List.of(skillId));
        Map<String, Object> resource = resource(skillId, "Initial resource", "https://example.org/qa");
        long careerId = ok(body(post("/api/admin/careers"), career), admin.session()).path("id").asLong();
        long resourceId = ok(body(post("/api/admin/resources"), resource), admin.session()).path("id").asLong();
        List<MockHttpServletRequestBuilder> denied = List.of(get("/api/admin/users"),
                body(post("/api/admin/careers"), career), body(put("/api/admin/careers/" + careerId), career),
                delete("/api/admin/careers/" + careerId), body(post("/api/admin/resources"), resource),
                body(put("/api/admin/resources/" + resourceId), resource), delete("/api/admin/resources/" + resourceId));
        for (MockHttpServletRequestBuilder request : denied) {
            mvc.perform(request).andExpect(status().isUnauthorized());
            mvc.perform(request.session(student.session())).andExpect(status().isForbidden());
        }
        JsonNode allUsers = ok(get("/api/admin/users"), admin.session());
        assertThat(ids(allUsers)).contains(student.id(), admin.id());
        assertNoPassword(allUsers);
        String updatedName = "Updated " + UUID.randomUUID();
        ok(body(put("/api/admin/careers/" + careerId), Map.of("name", updatedName, "description", "Changed",
                "skillIds", List.of(skillId))), admin.session());
        assertThat(ok(get("/api/careers/" + careerId), null).path("name").asText()).isEqualTo(updatedName);
        ok(body(put("/api/admin/resources/" + resourceId), resource(skillId, "Updated resource", "https://example.org/updated")), admin.session());
        JsonNode savedResource = findById(ok(get("/api/resources"), student.session()), resourceId);
        assertThat(savedResource.path("title").asText()).isEqualTo("Updated resource");
        assertThat(savedResource.path("url").asText()).isEqualTo("https://example.org/updated");
        ok(delete("/api/admin/resources/" + resourceId), admin.session());
        assertThat(ids(ok(get("/api/resources"), student.session()))).doesNotContain(resourceId);
        ok(delete("/api/admin/careers/" + careerId), admin.session());
        mvc.perform(get("/api/careers/" + careerId)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/careers/" + student.careerId()).session(admin.session()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gapUsesOnlyRequiredSkillsAndSkillUpsertDoesNotInflateReadiness() throws Exception {
        Account student = register();
        List<Long> required = requiredSkills(student);
        assertGap(student, List.of(), required, 0);
        long first = required.get(0);
        long id = ok(body(post("/api/users/" + student.id() + "/skills"), skill(first, "Beginner")), student.session()).path("id").asLong();
        long updatedId = ok(body(post("/api/skills"), skill(first, "Advanced")), student.session()).path("id").asLong();
        assertThat(updatedId).isEqualTo(id);
        long unrelated = ids(ok(get("/api/skills"), student.session())).stream().filter(s -> !required.contains(s)).findFirst().orElseThrow();
        ok(body(post("/api/skills"), skill(unrelated, "Advanced")), student.session());
        assertGap(student, List.of(first), required.subList(1, required.size()), readiness(1, required.size()));
        JsonNode dashboard = ok(get("/api/dashboard"), student.session());
        assertThat(dashboard.path("skills").size()).isEqualTo(2);
        assertThat(dashboard.path("gap").path("readiness").asDouble()).isEqualTo(readiness(1, required.size()));
        ok(delete("/api/skills/" + id), student.session());
        assertGap(student, List.of(), required, 0);
    }

    @Test
    void completingAndRevertingRoadmapUpdatesLearnedSkillsAndReadiness() throws Exception {
        Account student = register();
        List<Long> required = requiredSkills(student);
        JsonNode roadmap = ok(get("/api/roadmap"), student.session());
        assertThat(roadmap.size()).isEqualTo(required.size() + 1);
        assertThat(ids(ok(get("/api/roadmap"), student.session()))).containsExactlyElementsOf(ids(roadmap));
        JsonNode step = roadmap.get(0);
        long stepId = step.path("id").asLong();
        long skillId = step.path("skill").path("id").asLong();
        ok(body(put("/api/roadmap/" + stepId), Map.of("status", "IN_PROGRESS")), student.session());
        assertGap(student, List.of(), required, 0);
        ok(body(put("/api/roadmap/" + stepId), Map.of("status", "COMPLETED")), student.session());
        ok(body(put("/api/roadmap/" + stepId), Map.of("status", "COMPLETED")), student.session());
        JsonNode learned = ok(get("/api/dashboard"), student.session()).path("skills");
        assertThat(learned.size()).isEqualTo(1);
        assertThat(learned.get(0).path("skill").path("id").asLong()).isEqualTo(skillId);
        assertThat(learned.get(0).path("level").asText()).isEqualTo("Beginner");
        assertGap(student, List.of(skillId), required.stream().filter(id -> id != skillId).toList(), readiness(1, required.size()));
        assertThat(findById(ok(get("/api/roadmap"), student.session()), stepId).path("status").asText()).isEqualTo("COMPLETED");
        for (String status : List.of("IN_PROGRESS", "NOT_STARTED")) {
            ok(body(put("/api/roadmap/" + stepId), Map.of("status", status)), student.session());
            JsonNode dashboard = ok(get("/api/dashboard"), student.session());
            assertThat(dashboard.path("skills").size()).isZero();
            assertThat(findById(dashboard.path("roadmap"), stepId).path("status").asText()).isEqualTo(status);
            assertGap(student, List.of(), required, 0);
            ok(body(put("/api/roadmap/" + stepId), Map.of("status", "COMPLETED")), student.session());
        }
        long learnedId = ok(get("/api/dashboard"), student.session()).path("skills").get(0).path("id").asLong();
        ok(delete("/api/skills/" + learnedId), student.session());
        assertThat(findById(ok(get("/api/roadmap"), student.session()), stepId).path("status").asText()).isEqualTo("NOT_STARTED");
        JsonNode capstone = roadmap.get(roadmap.size() - 1);
        ok(body(put("/api/roadmap/" + capstone.path("id").asLong()), Map.of("status", "COMPLETED")), student.session());
        assertGap(student, List.of(), required, 0);
        assertThat(ok(get("/api/dashboard"), student.session()).path("skills").size()).isZero();
    }

    @Test
    void switchingCareerRejectsOldStepsAndNewCareerReadinessRoundsAndReachesOneHundred() throws Exception {
        Account student = register();
        Account admin = admin();
        long oldStepId = ok(get("/api/roadmap"), student.session()).get(0).path("id").asLong();
        List<Long> required = ids(ok(get("/api/skills"), student.session())).subList(0, 3);
        long careerId = ok(body(post("/api/admin/careers"), Map.of("name", "QA three skills " + UUID.randomUUID(),
                "description", "Rounding fixture", "skillIds", required)), admin.session()).path("id").asLong();
        ok(body(put("/api/profile"), Map.of("name", "QA Student", "careerId", careerId)), student.session());
        Account switched = new Account(student.id(), student.email(), careerId, student.session());
        mvc.perform(body(put("/api/roadmap/" + oldStepId).session(student.session()), Map.of("status", "COMPLETED")))
                .andExpect(status().isBadRequest());
        assertGap(switched, List.of(), required, 0);
        JsonNode steps = ok(get("/api/users/" + student.id() + "/roadmap"), student.session());
        assertThat(steps.size()).isEqualTo(4);
        List<Double> expectedReadiness = List.of(33.3, 66.7, 100.0);
        for (int i = 0; i < required.size(); i++) {
            JsonNode step = steps.get(i);
            assertThat(step.path("careerId").asLong()).isEqualTo(careerId);
            assertThat(step.path("position").asInt()).isEqualTo(i + 1);
            ok(body(put("/api/roadmap/" + step.path("id").asLong()), Map.of("status", "COMPLETED")), student.session());
            assertGap(switched, required.subList(0, i + 1), required.subList(i + 1, required.size()), expectedReadiness.get(i));
        }
        JsonNode dashboard = ok(get("/api/dashboard"), student.session());
        assertThat(dashboard.path("gap").path("readiness").asDouble()).isEqualTo(100.0);
        assertThat(dashboard.path("skills").size()).isEqualTo(3);
        assertThat(steps.get(3).path("skill").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\"QA\",\"email\":\"invalid\",\"password\":\"QaPassword123!\"}",
            "{\"name\":\" \",\"email\":\"valid@example.org\",\"password\":\"QaPassword123!\"}",
            "{\"name\":\"QA\",\"email\":\"valid@example.org\"}",
            "{\"name\":\"QA\",\"email\":\"valid@example.org\",\"password\":\"short\"}",
            "{"
    })
    void invalidRegistrationDoesNotCreateUserOrSession(String payload) throws Exception {
        long before = users.count();
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty()).andReturn();
        assertThat(users.count()).isEqualTo(before);
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void validationRejectsInvalidDomainValuesAndMissingRecordsWithoutMutatingWorkspace() throws Exception {
        Account student = register();
        long skillId = requiredSkills(student).get(0);
        long stepId = ok(get("/api/roadmap"), student.session()).get(0).path("id").asLong();
        List<MockHttpServletRequestBuilder> invalid = List.of(
                body(post("/api/auth/login"), Map.of("email", student.email())),
                body(post("/api/skills"), skill(skillId, "Expert")),
                body(post("/api/skills"), Map.of("level", "Beginner")),
                body(put("/api/roadmap/" + stepId), Map.of("status", "DONE")),
                body(post("/api/projects"), Map.of("name", "QA", "status", "DONE")),
                body(post("/api/projects"), Map.of("name", "QA", "status", "COMPLETED", "githubUrl", "javascript:alert(1)")),
                body(post("/api/projects"), Map.of("name", " ", "status", "COMPLETED")),
                body(post("/api/projects"), Map.of("name", "x".repeat(151), "status", "COMPLETED")),
                body(put("/api/profile"), Map.of("name", "QA")),
                body(post("/api/auth/register"), Map.of("name", "QA", "email", uniqueEmail(), "password", "\u00e9".repeat(37)))
        );
        for (MockHttpServletRequestBuilder request : invalid) {
            mvc.perform(request.session(student.session())).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
        mvc.perform(body(post("/api/skills").session(student.session()), skill(Long.MAX_VALUE, "Beginner")))
                .andExpect(status().isNotFound());
        mvc.perform(body(put("/api/profile").session(student.session()), Map.of("name", "QA", "careerId", Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
        mvc.perform(body(put("/api/roadmap/" + Long.MAX_VALUE).session(student.session()), Map.of("status", "COMPLETED")))
                .andExpect(status().isNotFound());
        JsonNode dashboard = ok(get("/api/dashboard"), student.session());
        assertThat(dashboard.path("skills").size()).isZero();
        assertThat(dashboard.path("projects").size()).isZero();
        assertThat(dashboard.path("user").path("name").asText()).isEqualTo("QA Student");

        Account admin = admin();
        for (MockHttpServletRequestBuilder request : List.of(
                body(post("/api/admin/careers"), Map.of("name", "QA", "description", "QA", "skillIds", List.of())),
                body(post("/api/admin/resources"), resource(skillId, "QA", "file:///tmp/test")))) {
            mvc.perform(request.session(admin.session())).andExpect(status().isBadRequest());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example", "http://localhost:81", "https://localhost", "null", "http://["})
    void foreignOrMalformedOriginCannotWriteOrLogOut(String origin) throws Exception {
        Account student = register();
        mvc.perform(body(post("/api/projects").session(student.session()).header("Origin", origin), project("Blocked")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Cross-site requests are not allowed"));
        mvc.perform(post("/api/auth/logout").session(student.session()).header("Origin", origin))
                .andExpect(status().isForbidden());
        assertThat(student.session().isInvalid()).isFalse();
        assertThat(ok(get("/api/projects"), student.session()).size()).isZero();
        mvc.perform(body(post("/api/auth/register").header("Origin", origin),
                Map.of("name", "QA", "email", uniqueEmail(), "password", PASSWORD))).andExpect(status().isForbidden());
    }

    @Test
    void sameOriginWritesAreAllowedAndFetchMetadataBlocksCrossSiteWrites() throws Exception {
        Account student = register();
        for (String origin : List.of("http://localhost", "http://localhost:80", "http://LOCALHOST")) {
            ok(body(post("/api/projects").header("Origin", origin), project("Allowed")), student.session());
        }
        mvc.perform(body(post("/api/projects").session(student.session()).header("Sec-Fetch-Site", "cross-site"), project("Blocked")))
                .andExpect(status().isForbidden());
        mvc.perform(body(post("/api/projects").session(student.session()).header("Origin", "http://localhost")
                .header("Sec-Fetch-Site", "cross-site"), project("Blocked"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/projects").session(student.session()).header("Origin", "https://evil.example"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
        assertThat(ok(get("/api/projects"), student.session()).size()).isEqualTo(3);
    }

    private Account register() throws Exception {
        String email = uniqueEmail();
        MvcResult result = mvc.perform(body(post("/api/auth/register"),
                        Map.of("name", "QA Student", "email", email, "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        JsonNode user = tree(result);
        return new Account(user.path("id").asLong(), email, user.path("careerId").asLong(),
                (MockHttpSession) result.getRequest().getSession(false));
    }

    private Account admin() throws Exception {
        Account account = register();
        // Bootstrap only the role; authentication and all exercised behavior still use HTTP.
        User user = users.findById(account.id()).orElseThrow();
        user.role = "ADMIN";
        users.saveAndFlush(user);
        return account;
    }

    private List<Long> requiredSkills(Account account) throws Exception {
        return ids(ok(get("/api/careers/" + account.careerId()), null).path("skills"));
    }

    private void assertGap(Account account, List<Long> completed, List<Long> missing, double readiness) throws Exception {
        JsonNode gap = ok(get("/api/users/" + account.id() + "/skill-gap/" + account.careerId()), account.session());
        assertThat(ids(gap.path("completed"))).containsExactlyInAnyOrderElementsOf(completed);
        assertThat(ids(gap.path("missing"))).containsExactlyInAnyOrderElementsOf(missing);
        assertThat(gap.path("readiness").asDouble()).isEqualTo(readiness);
    }

    private static double readiness(int learned, int total) {
        return Math.round(learned * 1000.0 / total) / 10.0;
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object value) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(value));
    }

    private JsonNode ok(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        if (session != null) request.session(session);
        return tree(mvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    private JsonNode tree(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private static List<Long> ids(JsonNode nodes) {
        List<Long> ids = new ArrayList<>();
        nodes.forEach(node -> ids.add(node.path("id").asLong()));
        return ids;
    }

    private static JsonNode findById(JsonNode nodes, long id) {
        for (JsonNode node : nodes) if (node.path("id").asLong() == id) return node;
        throw new AssertionError("Missing record " + id + " in " + nodes);
    }

    private static void assertNoPassword(JsonNode node) {
        assertThat(node.has("password")).isFalse();
        assertThat(node.has("passwordHash")).isFalse();
        assertThat(node.toString()).doesNotContain(PASSWORD, "$2a$", "$2b$");
        if (node.isContainerNode()) node.forEach(WorkspaceApiIntegrationTest::assertNoPassword);
    }

    private static String uniqueEmail() { return "qa-" + UUID.randomUUID() + "@example.org"; }
    private static Map<String, Object> skill(long id, String level) { return Map.of("skillId", id, "level", level); }
    private static Map<String, Object> project(String name) { return Map.of("name", name, "status", "IN_PROGRESS"); }
    private static Map<String, Object> resource(long skillId, String title, String url) {
        return Map.of("skillId", skillId, "title", title, "url", url, "type", "Guide");
    }
}
