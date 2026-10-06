package com.careerforge.v2.extras;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class ExtrasDtos {
    private ExtrasDtos() {}
    public record ResumeFields(
        @Size(max=200) String headline, @Size(max=4000) String summary,
        @Size(max=60) String phone, @Size(max=200) String location,
        @Size(max=1000) String website, @Size(max=4000) String education,
        @Size(max=4000) String experience, @Size(max=4000) String achievements,
        @NotNull Boolean includeSkills, @NotNull Boolean includeProjects,
        @NotNull Boolean includeEducation, @NotNull Boolean includeExperience,
        @NotNull Boolean includeAchievements) {}
    public record ResumeUser(String name,String email,String course,String college,String currentYear) {}
    public record SkillView(Long id,String name) {}
    public record LearnedSkill(Long id,SkillView skill,String level) {}
    public record ProjectView(Long id,String name,String description,String technology,String githubUrl,String status) {}
    public record ResumeBundle(ResumeFields profile,ResumeUser user,List<LearnedSkill> skills,List<ProjectView> projects) {}
    public record CareerView(Long id,String name,String description,List<SkillView> skills) {}
    public record GapView(List<SkillView> completed,List<SkillView> missing,double readiness) {}
    public record ComparisonSide(CareerView career,GapView gap,int estimatedHours) {}
    public record Comparison(ComparisonSide left,ComparisonSide right,List<SkillView> sharedSkills,String estimateExplanation) {}
    public record EventView(Long id,String type,String title,double readiness,Instant occurredAt,Long careerId) {}
    public record TrendPoint(String date,double readiness,Long careerId) {}
    public record Progress(List<EventView> events,List<TrendPoint> trend,int streak,String currentTimezone,String streakDefinition) {}
    public record Recommendation(String id,String title,String description,String difficulty,
        List<String> technologies,List<String> prerequisites,List<String> milestones,List<Long> careerIds,
        boolean saved,Long addedProjectId) {}
}
