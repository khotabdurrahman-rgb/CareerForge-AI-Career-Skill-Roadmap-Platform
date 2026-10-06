package com.careerforge.v2.learning;

import com.careerforge.service.AuthService;
import com.careerforge.v2.learning.LearningDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LearningController {
    private final AuthService auth;
    private final AssessmentService assessments;
    private final PlannerService planner;
    public LearningController(AuthService auth,AssessmentService assessments,PlannerService planner) {
        this.auth=auth; this.assessments=assessments; this.planner=planner;
    }
    @GetMapping("/assessments")
    public Assessments assessments(HttpServletRequest request) { return assessments.overview(auth.current(request)); }
    @PostMapping("/assessments/{skillId}/start")
    public Started start(@PathVariable Long skillId,HttpServletRequest request) { return assessments.start(auth.current(request),skillId); }
    @PostMapping("/assessments/sessions/{sessionId}/submit")
    public Submitted submit(@PathVariable String sessionId,@Valid @RequestBody Submission input,HttpServletRequest request) {
        return assessments.submit(auth.current(request),sessionId,input);
    }
    @GetMapping("/planner")
    public Planner planner(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate week,HttpServletRequest request) {
        return planner.overview(auth.current(request),week);
    }
    @PostMapping("/planner/goals")
    public Goal goal(@Valid @RequestBody GoalInput input,HttpServletRequest request) { return planner.saveGoal(auth.current(request),null,input); }
    @PutMapping("/planner/goals/{id}")
    public Goal goal(@PathVariable Long id,@Valid @RequestBody GoalInput input,HttpServletRequest request) { return planner.saveGoal(auth.current(request),id,input); }
    @DeleteMapping("/planner/goals/{id}")
    public Map<String,String> deleteGoal(@PathVariable Long id,HttpServletRequest request) {
        planner.deleteGoal(auth.current(request),id); return Map.of("message","Goal removed");
    }
    @PostMapping("/planner/tasks")
    public Task task(@Valid @RequestBody TaskInput input,HttpServletRequest request) { return planner.saveTask(auth.current(request),null,input); }
    @PutMapping("/planner/tasks/{id}")
    public Task task(@PathVariable Long id,@Valid @RequestBody TaskInput input,HttpServletRequest request) { return planner.saveTask(auth.current(request),id,input); }
    @DeleteMapping("/planner/tasks/{id}")
    public Map<String,String> deleteTask(@PathVariable Long id,HttpServletRequest request) {
        planner.deleteTask(auth.current(request),id); return Map.of("message","Task removed");
    }
}
