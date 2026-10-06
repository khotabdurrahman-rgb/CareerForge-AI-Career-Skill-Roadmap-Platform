package com.careerforge.v2.learning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class LearningDtos {
    private LearningDtos() {}
    public record Question(String id,String prompt,List<String> options) {}
    public record Started(String id,Long skillId,String skillName,Instant expiresAt,List<Question> questions) {}
    public record Answer(@NotBlank String questionId,@NotNull @Min(0) Integer optionIndex) {}
    public record Submission(@NotNull @Size(min=3,max=5) List<@NotNull @Valid Answer> answers) {}
    public record Attempt(Long id,Long skillId,String skillName,double score,int correct,int total,Instant completedAt) {}
    public record Feedback(String questionId,boolean correct,int correctOptionIndex,String explanation) {}
    public record Submitted(Attempt attempt,List<Feedback> feedback) {}
    public record SkillSummary(Long skillId,String name,int questionCount,Double bestScore,long attemptCount) {}
    public record Assessments(List<SkillSummary> skills,List<Attempt> attempts) {}
    public record GoalInput(@NotBlank @Size(max=255) String title,@NotNull LocalDate weekStart,
                            @NotNull @Min(1) @Max(10080) Integer targetMinutes) {}
    public record TaskInput(@NotBlank @Size(max=255) String title,Long skillId,Long roadmapStepId,
                            @NotNull LocalDate dueDate,@NotNull @Min(1) @Max(1440) Integer estimatedMinutes,
                            @NotBlank String status,@NotNull LocalDate weekStart) {}
    public record Goal(Long id,String title,LocalDate weekStart,int targetMinutes) {}
    public record Task(Long id,String title,Long skillId,Long roadmapStepId,LocalDate dueDate,
                       int estimatedMinutes,String status,LocalDate weekStart) {}
    public record Planner(LocalDate weekStart,LocalDate weekEnd,String timezone,List<Goal> goals,
                          List<Task> tasks,List<Task> overdue,long plannedMinutes,long completedMinutes) {}
}
