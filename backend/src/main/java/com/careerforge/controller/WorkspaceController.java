package com.careerforge.controller;

import com.careerforge.dto.Requests.*;
import com.careerforge.model.*;
import com.careerforge.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController
@RequestMapping("/api")
public class WorkspaceController {
    private final CareerService data;
    private final AuthService auth;
    public WorkspaceController(CareerService data,AuthService auth) { this.data=data; this.auth=auth; }
    @GetMapping("/dashboard")
    public Map<String,Object> dashboard(HttpServletRequest request) { return data.dashboard(auth.current(request)); }
    @GetMapping("/skills")
    public List<Skill> skills(HttpServletRequest request) { auth.current(request); return data.skills().findAll(); }
    @GetMapping("/careers")
    public List<Career> careers() { return data.careers().findAll(); }
    @GetMapping("/careers/{id}")
    public Career career(@PathVariable Long id) { return data.career(id); }
    @PutMapping("/profile")
    public User profile(@Valid @RequestBody Profile input,HttpServletRequest request) { return data.profile(auth.current(request),input); }
    @PostMapping("/skills")
    public UserSkill addSkill(@Valid @RequestBody SkillInput input,HttpServletRequest request) { return data.addSkill(auth.current(request),input); }
    @DeleteMapping("/skills/{id}")
    public Map<String,String> removeSkill(@PathVariable Long id,HttpServletRequest request) {
        data.removeSkill(auth.current(request),id); return Map.of("message","Skill removed");
    }
    @GetMapping("/roadmap")
    public List<RoadmapStep> roadmap(HttpServletRequest request) { return data.generateRoadmap(auth.current(request)); }
    @PutMapping("/roadmap/{id}")
    public RoadmapStep updateStep(@PathVariable Long id,@Valid @RequestBody Status input,HttpServletRequest request) {
        return data.updateStep(auth.current(request),id,input);
    }
    @GetMapping("/projects")
    public List<Project> projects(HttpServletRequest request) { return data.projects().findByUserIdOrderByIdDesc(auth.current(request).id); }
    @PostMapping("/projects")
    public Project project(@Valid @RequestBody ProjectInput input,HttpServletRequest request) { return data.saveProject(auth.current(request),null,input); }
    @PutMapping("/projects/{id}")
    public Project editProject(@PathVariable Long id,@Valid @RequestBody ProjectInput input,HttpServletRequest request) { return data.saveProject(auth.current(request),id,input); }
    @DeleteMapping("/projects/{id}")
    public Map<String,String> deleteProject(@PathVariable Long id,HttpServletRequest request) {
        data.removeProject(auth.current(request),id); return Map.of("message","Project removed");
    }
    @GetMapping("/resources")
    public List<Resource> resources(HttpServletRequest request) { auth.current(request); return data.resources().findAll(); }
    private User owner(Long id,HttpServletRequest request) {
        User user=auth.current(request);
        if(!id.equals(user.id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You can only access your own workspace");
        return user;
    }
    @GetMapping("/users/{id}/skill-gap/{careerId}")
    public Map<String,Object> gap(@PathVariable Long id,@PathVariable Long careerId,HttpServletRequest request) {
        return data.gap(owner(id,request),careerId);
    }
    @PostMapping("/users/{id}/skills")
    public UserSkill skillForUser(@PathVariable Long id,@Valid @RequestBody SkillInput input,HttpServletRequest request) {
        return data.addSkill(owner(id,request),input);
    }
    @GetMapping("/users/{id}/roadmap")
    public List<RoadmapStep> roadmapForUser(@PathVariable Long id,HttpServletRequest request) { return data.generateRoadmap(owner(id,request)); }
    @GetMapping("/users/{id}/projects")
    public List<Project> projectsForUser(@PathVariable Long id,HttpServletRequest request) { return data.projects().findByUserIdOrderByIdDesc(owner(id,request).id); }
    @PostMapping("/users/{id}/projects")
    public Project projectForUser(@PathVariable Long id,@Valid @RequestBody ProjectInput input,HttpServletRequest request) {
        return data.saveProject(owner(id,request),null,input);
    }
}
