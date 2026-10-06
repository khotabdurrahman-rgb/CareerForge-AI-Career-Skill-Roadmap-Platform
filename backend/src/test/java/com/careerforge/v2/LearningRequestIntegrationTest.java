package com.careerforge.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.careerforge.v2.learning.QuizSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:v2-learning-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "careerforge.seed-demo=false", "careerforge.ai.api-key=", "spring.jpa.open-in-view=false",
        "spring.mail.host=", "careerforge.mail.mode=smtp"
})
@AutoConfigureMockMvc
class LearningRequestIntegrationTest extends V2RequestTestSupport {
    @Autowired QuizSessionRepository sessions;
    @Test
    void assessmentsKeepAnswersPrivateAndPersistOnlyOneAttemptPerSession() throws Exception {
        Account owner = register();
        Account other = register();
        JsonNode catalog = ok(get("/api/assessments"), owner);
        assertThat(catalog.path("skills").isArray()).isTrue();
        assertThat(catalog.path("skills").isEmpty()).isFalse();
        long skillId = catalog.path("skills").get(0).path("skillId").asLong();
        JsonNode skillsBefore = ok(get("/api/dashboard"), owner).path("skills");
        JsonNode started = ok(post("/api/assessments/" + skillId + "/start"), owner);
        String endpoint = "/api/assessments/sessions/" + started.path("id").asText() + "/submit";
        List<Map<String, Object>> answers = new ArrayList<>();
        assertThat(started.path("questions").size()).isBetween(3, 5);
        for (JsonNode question : started.path("questions")) {
            assertThat(question.path("prompt").asText()).isNotBlank();
            assertThat(question.path("options").size()).isGreaterThanOrEqualTo(2);
            assertThat(question.has("correctOptionIndex")).isFalse();
            assertThat(question.has("answer")).isFalse();
            assertThat(question.has("explanation")).isFalse();
            answers.add(Map.of("questionId", question.path("id").asText(), "optionIndex", 0));
        }
        noSecrets(started);
        mvc.perform(body(post(endpoint).session(other.session()), Map.of("answers", answers)))
                .andExpect(status().isNotFound());
        assertThat(ok(get("/api/assessments"), other).path("attempts").isEmpty()).isTrue();
        mvc.perform(body(post(endpoint).session(owner.session()), Map.of("answers", List.of())))
                .andExpect(status().isBadRequest());
        List<Map<String, Object>> duplicate = new ArrayList<>(answers);
        duplicate.set(1, duplicate.get(0));
        mvc.perform(body(post(endpoint).session(owner.session()), Map.of("answers", duplicate)))
                .andExpect(status().isBadRequest());
        List<Map<String, Object>> unknown = new ArrayList<>(answers);
        unknown.set(0, Map.of("questionId", "unknown-question", "optionIndex", 0));
        mvc.perform(body(post(endpoint).session(owner.session()), Map.of("answers", unknown)))
                .andExpect(status().isBadRequest());
        List<Map<String, Object>> invalidOption = new ArrayList<>(answers);
        invalidOption.set(0, Map.of("questionId", answers.get(0).get("questionId"), "optionIndex", 999));
        mvc.perform(body(post(endpoint).session(owner.session()), Map.of("answers", invalidOption)))
                .andExpect(status().isBadRequest());
        JsonNode submitted = ok(body(post(endpoint), Map.of("answers", answers)), owner);
        JsonNode attempt = submitted.path("attempt");
        assertThat(attempt.path("skillId").asLong()).isEqualTo(skillId);
        assertThat(attempt.path("total").asInt()).isEqualTo(answers.size());
        long correct = 0;
        for (JsonNode feedback : submitted.path("feedback")) {
            if (feedback.path("correct").asBoolean()) correct++;
            assertThat(feedback.path("explanation").asText()).isNotBlank();
        }
        assertThat(submitted.path("feedback").size()).isEqualTo(answers.size());
        assertThat(attempt.path("correct").asLong()).isEqualTo(correct);
        assertThat(attempt.path("score").asDouble()).isCloseTo(correct * 100.0 / answers.size(),
                org.assertj.core.data.Offset.offset(0.11));
        mvc.perform(body(post(endpoint).session(owner.session()), Map.of("answers", answers)))
                .andExpect(status().isConflict());
        JsonNode persisted = find(ok(get("/api/assessments"), owner).path("attempts"), attempt.path("id").asLong());
        for (String field : List.of("id", "skillId", "skillName", "score", "correct", "total"))
            assertThat(persisted.path(field)).as("Persisted attempt %s", field).isEqualTo(attempt.path(field));
        assertThat(Instant.parse(persisted.path("completedAt").asText()).truncatedTo(ChronoUnit.MICROS))
                .isEqualTo(Instant.parse(attempt.path("completedAt").asText()).truncatedTo(ChronoUnit.MICROS));
        assertThat(ok(get("/api/dashboard"), owner).path("skills")).isEqualTo(skillsBefore);
        for (JsonNode summary : ok(get("/api/assessments"), owner).path("skills")) {
            if (summary.path("skillId").asLong() == skillId) {
                assertThat(summary.path("attemptCount").asInt()).isEqualTo(1);
                assertThat(summary.path("bestScore").asDouble()).isEqualTo(attempt.path("score").asDouble());
            }
        }
    }

    @Test
    void expiredQuizCannotCreateAttemptOrConsumeSession() throws Exception {
        Account owner = register();
        long skillId = ok(get("/api/assessments"), owner).path("skills").get(0).path("skillId").asLong();
        JsonNode quiz = ok(post("/api/assessments/" + skillId + "/start"), owner);
        String id = quiz.path("id").asText();
        var expired = sessions.findById(id).orElseThrow();
        expired.expiresAt = Instant.now().minusSeconds(60);
        sessions.saveAndFlush(expired);
        List<Map<String, Object>> answers = new ArrayList<>();
        quiz.path("questions").forEach(q -> answers.add(Map.of("questionId", q.path("id").asText(), "optionIndex", 0)));
        mvc.perform(body(post("/api/assessments/sessions/" + id + "/submit").session(owner.session()), Map.of("answers", answers)))
                .andExpect(status().isGone());
        assertThat(sessions.findById(id).orElseThrow().submitted).isFalse();
        assertThat(ok(get("/api/assessments"), owner).path("attempts").isEmpty()).isTrue();
    }

    @Test
    void plannerCrudEnforcesOwnershipAndCompletingLinkedTaskCompletesRoadmap() throws Exception {
        Account owner = register();
        Account other = register();
        LocalDate week = LocalDate.of(2026, 10, 5);
        String query = "/api/planner?week=" + week;
        JsonNode step = ok(get("/api/roadmap"), owner).get(0);
        long goalId = ok(body(post("/api/planner/goals"), Map.of("title", "QA goal",
                "weekStart", week.toString(), "targetMinutes", 120)), owner).path("id").asLong();
        Map<String, Object> task = task(week, "NOT_STARTED", step.path("id").asLong(), step.path("skill").path("id").asLong());
        long taskId = ok(body(post("/api/planner/tasks"), task), owner).path("id").asLong();
        assertThat(find(ok(get(query), owner).path("goals"), goalId).path("targetMinutes").asInt()).isEqualTo(120);
        assertThat(find(ok(get(query), owner).path("tasks"), taskId).path("roadmapStepId").asLong())
                .isEqualTo(step.path("id").asLong());
        mvc.perform(delete("/api/planner/goals/" + goalId).session(other.session())).andExpect(status().isNotFound());
        mvc.perform(body(put("/api/planner/tasks/" + taskId).session(other.session()), task)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/planner/tasks/" + taskId).session(other.session())).andExpect(status().isNotFound());
        ok(body(put("/api/planner/goals/" + goalId), Map.of("title", "Updated QA goal",
                "weekStart", week.toString(), "targetMinutes", 180)), owner);
        ok(body(put("/api/planner/tasks/" + taskId), task(week, "COMPLETED",
                step.path("id").asLong(), step.path("skill").path("id").asLong())), owner);
        assertThat(find(ok(get("/api/roadmap"), owner), step.path("id").asLong()).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(find(ok(get(query), owner).path("goals"), goalId).path("title").asText()).isEqualTo("Updated QA goal");
        ok(delete("/api/planner/tasks/" + taskId), owner);
        ok(delete("/api/planner/goals/" + goalId), owner);
        for (JsonNode row : ok(get(query), owner).path("tasks")) assertThat(row.path("id").asLong()).isNotEqualTo(taskId);
        for (JsonNode row : ok(get(query), owner).path("goals")) assertThat(row.path("id").asLong()).isNotEqualTo(goalId);
        mvc.perform(get("/api/planner?week=invalid").session(owner.session())).andExpect(status().isBadRequest());
        mvc.perform(body(post("/api/planner/goals").session(owner.session()), Map.of(
                "title", "", "weekStart", week.toString(), "targetMinutes", 0))).andExpect(status().isBadRequest());
        JsonNode foreign = ok(get("/api/roadmap"), other).get(0);
        mvc.perform(body(post("/api/planner/tasks").session(owner.session()), task(week, "NOT_STARTED",
                foreign.path("id").asLong(), foreign.path("skill").path("id").asLong())))
                .andExpect(status().isNotFound());
    }

    private Map<String, Object> task(LocalDate week, String status, long stepId, long skillId) {
        return Map.of("title", "QA linked task", "skillId", skillId, "roadmapStepId", stepId,
                "dueDate", week.plusDays(1).toString(), "estimatedMinutes", 45, "status", status, "weekStart", week.toString());
    }

    @Test
    void plannerNormalizesWeeksSupportsUnlinkedTasksAndOverdueExcludesCompletedTasks() throws Exception {
        Account owner = register();
        Account other = register();
        LocalDate monday = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"))
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).minusWeeks(1);
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("title", "  Nullable QA task  ");
        task.put("skillId", null);
        task.put("roadmapStepId", null);
        task.put("dueDate", monday.toString());
        task.put("weekStart", monday.plusDays(2).toString());
        task.put("estimatedMinutes", 40);
        task.put("status", "NOT_STARTED");
        JsonNode created = ok(body(post("/api/planner/tasks"), task), owner);
        long id = created.path("id").asLong();
        assertThat(created.path("title").asText()).isEqualTo("Nullable QA task");
        assertThat(created.path("weekStart").asText()).isEqualTo(monday.toString());
        assertThat(created.path("skillId").isNull()).isTrue();
        assertThat(created.path("roadmapStepId").isNull()).isTrue();
        JsonNode oldWeek = ok(get("/api/planner?week=" + monday.plusDays(5)), owner);
        assertThat(oldWeek.path("plannedMinutes").asInt()).isEqualTo(40);
        assertThat(oldWeek.path("completedMinutes").asInt()).isZero();
        assertThat(find(ok(get("/api/planner?week=" + monday.plusWeeks(3)), owner).path("overdue"), id).path("id").asLong()).isEqualTo(id);
        for (JsonNode row : ok(get("/api/planner"), other).path("overdue"))
            assertThat(row.path("id").asLong()).isNotEqualTo(id);
        task.put("status", "COMPLETED");
        ok(body(put("/api/planner/tasks/" + id), task), owner);
        assertThat(ok(get("/api/planner?week=" + monday), owner).path("completedMinutes").asInt()).isEqualTo(40);
        for (JsonNode row : ok(get("/api/planner"), owner).path("overdue"))
            assertThat(row.path("id").asLong()).isNotEqualTo(id);
        task.put("dueDate", monday.plusWeeks(1).toString());
        mvc.perform(body(put("/api/planner/tasks/" + id).session(owner.session()), task)).andExpect(status().isBadRequest());
        assertThat(find(ok(get("/api/planner?week=" + monday), owner).path("tasks"), id).path("dueDate").asText()).isEqualTo(monday.toString());
    }

    @Test
    void v2LearningReadsRequireSignIn() throws Exception {
        for (String path : List.of("/api/assessments", "/api/planner?week=2026-10-05"))
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
}
