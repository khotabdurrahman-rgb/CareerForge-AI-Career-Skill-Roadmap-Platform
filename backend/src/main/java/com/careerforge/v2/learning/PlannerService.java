package com.careerforge.v2.learning;

import com.careerforge.dto.Requests.Status;
import com.careerforge.model.*;
import com.careerforge.service.CareerService;
import com.careerforge.v2.extras.ProgressService;
import com.careerforge.v2.learning.LearningDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly=true)
public class PlannerService {
    private final CareerService data;
    private final PlannerGoalRepository goals;
    private final PlannerTaskRepository tasks;
    private final ProgressService progress;
    public PlannerService(CareerService data,PlannerGoalRepository goals,PlannerTaskRepository tasks,ProgressService progress) {
        this.data=data; this.goals=goals; this.tasks=tasks; this.progress=progress;
    }
    private LocalDate monday(LocalDate date) { return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
    private ZoneId zone(User user) {
        try { return ZoneId.of(user.timezone==null || user.timezone.isBlank() ? "Asia/Kolkata" : user.timezone); }
        catch(DateTimeException e) { throw CareerService.bad("Invalid profile timezone"); }
    }
    public Planner overview(User user,LocalDate week) {
        ZoneId timezone=zone(user);
        LocalDate today=LocalDate.now(timezone), start=monday(week==null ? today : week);
        var selected=tasks.findByUserIdAndWeekStartOrderByDueDateAscIdAsc(user.id,start);
        return new Planner(start,start.plusDays(6),timezone.getId(),
                goals.findByUserIdAndWeekStartOrderByIdAsc(user.id,start).stream().map(this::view).toList(),
                selected.stream().map(this::view).toList(),
                tasks.findByUserIdAndDueDateBeforeAndStatusNotOrderByDueDateAscIdAsc(user.id,today,"COMPLETED").stream().map(this::view).toList(),
                selected.stream().mapToLong(t->t.estimatedMinutes).sum(),
                selected.stream().filter(t->"COMPLETED".equals(t.status)).mapToLong(t->t.estimatedMinutes).sum());
    }
    @Transactional
    public Goal saveGoal(User user,Long id,GoalInput input) {
        if(input==null || input.weekStart()==null || input.targetMinutes()==null || input.targetMinutes()<1 || input.targetMinutes()>10080)
            throw CareerService.bad("Target minutes must be between 1 and 10080 and weekStart is required");
        PlannerGoal goal=id==null ? new PlannerGoal() : goals.findByIdAndUserId(id,user.id).orElseThrow(()->CareerService.missing("Goal not found"));
        Goal desired=new Goal(id,title(input.title()),monday(input.weekStart()),input.targetMinutes());
        if(id!=null && desired.equals(view(goal))) return view(goal);
        goal.user=user; goal.title=desired.title(); goal.weekStart=desired.weekStart(); goal.targetMinutes=desired.targetMinutes();
        goals.save(goal);
        record(user,id==null ? "PLANNER_GOAL_CREATED" : "PLANNER_GOAL_UPDATED",goal.title);
        return view(goal);
    }
    @Transactional
    public void deleteGoal(User user,Long id) {
        var goal=goals.findByIdAndUserId(id,user.id).orElseThrow(()->CareerService.missing("Goal not found"));
        goals.delete(goal); record(user,"PLANNER_GOAL_DELETED",goal.title);
    }
    @Transactional
    public Task saveTask(User user,Long id,TaskInput input) {
        if(input==null || input.weekStart()==null || input.dueDate()==null || input.estimatedMinutes()==null
                || input.estimatedMinutes()<1 || input.estimatedMinutes()>1440)
            throw CareerService.bad("Dates are required and estimated minutes must be between 1 and 1440");
        if(input.status()==null || !Set.of("NOT_STARTED","IN_PROGRESS","COMPLETED").contains(input.status()))
            throw CareerService.bad("Invalid task status");
        LocalDate start=monday(input.weekStart());
        if(!monday(input.dueDate()).equals(start)) throw CareerService.bad("Task due date must fall within its week");
        PlannerTask task=id==null ? new PlannerTask() : tasks.findByIdAndUserId(id,user.id).orElseThrow(()->CareerService.missing("Task not found"));
        String normalizedTitle=title(input.title());
        Skill skill=input.skillId()==null ? null : data.skill(input.skillId());
        if(input.roadmapStepId()!=null) {
            RoadmapStep step=data.roadmap().findById(input.roadmapStepId()).orElseThrow(()->CareerService.missing("Roadmap step not found"));
            if(!step.user.id.equals(user.id)) throw CareerService.missing("Roadmap step not found");
            if(!step.careerId.equals(user.careerId)) throw CareerService.bad("Roadmap step must belong to the active career");
            if(skill!=null && (step.skill==null || !skill.id.equals(step.skill.id)))
                throw CareerService.bad("Task skill must match its roadmap step");
            if(skill==null) skill=step.skill;
        }
        Task desired=new Task(id,normalizedTitle,skill==null ? null : skill.id,input.roadmapStepId(),input.dueDate(),
                input.estimatedMinutes(),input.status(),start);
        if(id!=null && desired.equals(view(task))) return view(task);
        boolean completed="COMPLETED".equals(input.status()) && (id==null || !"COMPLETED".equals(task.status)
                || !Objects.equals(task.roadmapStepId,input.roadmapStepId()));
        if(input.roadmapStepId()!=null && "COMPLETED".equals(input.status()))
            data.updateStep(user,input.roadmapStepId(),new Status("COMPLETED"));
        task.user=user; task.title=normalizedTitle; task.skill=skill; task.roadmapStepId=input.roadmapStepId();
        task.dueDate=input.dueDate(); task.weekStart=start; task.estimatedMinutes=input.estimatedMinutes(); task.status=input.status();
        tasks.save(task);
        record(user,completed ? "PLANNER_TASK_COMPLETED" : id==null ? "PLANNER_TASK_CREATED" : "PLANNER_TASK_UPDATED",task.title);
        return view(task);
    }
    @Transactional
    public void deleteTask(User user,Long id) {
        var task=tasks.findByIdAndUserId(id,user.id).orElseThrow(()->CareerService.missing("Task not found"));
        tasks.delete(task); record(user,"PLANNER_TASK_DELETED",task.title);
    }
    private void record(User user,String type,String title) {
        progress.record(user,type,title,((Number)data.gap(user,user.careerId).get("readiness")).doubleValue());
    }
    private String title(String title) {
        if(title==null || title.isBlank() || title.trim().length()>255) throw CareerService.bad("Title must contain 1 to 255 characters");
        return title.trim();
    }
    private Goal view(PlannerGoal g) { return new Goal(g.id,g.title,g.weekStart,g.targetMinutes); }
    private Task view(PlannerTask t) { return new Task(t.id,t.title,t.skill==null ? null : t.skill.id,t.roadmapStepId,t.dueDate,t.estimatedMinutes,t.status,t.weekStart); }
}
