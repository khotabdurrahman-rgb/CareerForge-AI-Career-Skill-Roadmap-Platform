package com.careerforge.service;

import com.careerforge.model.*;
import com.careerforge.repository.*;
import com.careerforge.dto.Requests.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.util.*;

@Service
@Transactional
public class CareerService {
    private final UserRepository users;
    private final SkillRepository skills;
    private final CareerRepository careers;
    private final UserSkillRepository userSkills;
    private final RoadmapStepRepository roadmap;
    private final ProjectRepository projects;
    private final ResourceRepository resources;

    public CareerService(UserRepository users,SkillRepository skills,CareerRepository careers,
                         UserSkillRepository userSkills,RoadmapStepRepository roadmap,
                         ProjectRepository projects,ResourceRepository resources) {
        this.users=users; this.skills=skills; this.careers=careers; this.userSkills=userSkills;
        this.roadmap=roadmap; this.projects=projects; this.resources=resources;
    }
    public UserRepository users() { return users; }
    public SkillRepository skills() { return skills; }
    public CareerRepository careers() { return careers; }
    public UserSkillRepository userSkills() { return userSkills; }
    public RoadmapStepRepository roadmap() { return roadmap; }
    public ProjectRepository projects() { return projects; }
    public ResourceRepository resources() { return resources; }
    public static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    public static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND,message); }
    public User user(Long id) { return users.findById(id).orElseThrow(()->missing("User not found")); }
    public Career career(Long id) { return careers.findById(id).orElseThrow(()->missing("Career not found")); }
    public Skill skill(Long id) { return skills.findById(id).orElseThrow(()->missing("Skill not found")); }
    private void owns(Long owner,User user) {
        if(!owner.equals(user.id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This record belongs to another student");
    }
    public Map<String,Object> gap(User user,Long careerId) {
        Career career=career(careerId);
        Set<Long> learned=new HashSet<>();
        userSkills.findByUserIdOrderByIdAsc(user.id).forEach(s->learned.add(s.skill.id));
        List<Skill> completed=career.skills.stream().filter(s->learned.contains(s.id)).toList();
        List<Skill> missing=career.skills.stream().filter(s->!learned.contains(s.id)).toList();
        double readiness=career.skills.isEmpty() ? 0 : Math.round(completed.size()*1000.0/career.skills.size())/10.0;
        return Map.of("completed",completed,"missing",missing,"readiness",readiness);
    }
    public List<RoadmapStep> generateRoadmap(User user) {
        Career career=career(user.careerId);
        List<RoadmapStep> existing=roadmap.findByUserIdAndCareerIdOrderByPositionAsc(user.id,career.id);
        Set<Long> learned=new HashSet<>();
        userSkills.findByUserIdOrderByIdAsc(user.id).forEach(s->learned.add(s.skill.id));
        Set<Long> required=new HashSet<>();
        career.skills.forEach(s->required.add(s.id));
        for(RoadmapStep step:new ArrayList<>(existing)) {
            if(step.skill!=null && !required.contains(step.skill.id)) {
                roadmap.delete(step); existing.remove(step);
            }
        }
        int position=1;
        for(Skill skill:career.skills) {
            RoadmapStep step=existing.stream().filter(s->s.skill!=null && s.skill.id.equals(skill.id)).findFirst().orElse(null);
            if(step==null && !learned.contains(skill.id)) {
                step=new RoadmapStep(); step.user=user; step.careerId=career.id; step.skill=skill;
                step.title="Learn "+skill.name; existing.add(step);
            }
            if(step!=null) {
                step.position=position;
                if(learned.contains(skill.id)) step.status="COMPLETED";
                else if("COMPLETED".equals(step.status)) step.status="NOT_STARTED";
                roadmap.save(step);
                position++;
            }
        }
        RoadmapStep capstone=existing.stream().filter(s->s.skill==null).findFirst().orElse(null);
        if(capstone==null) {
            capstone=new RoadmapStep(); capstone.user=user; capstone.careerId=career.id;
            capstone.title="Build a portfolio-ready project"; existing.add(capstone);
        }
        capstone.position=position; roadmap.save(capstone);
        return existing.stream().sorted(Comparator.comparingInt(s->s.position)).toList();
    }
    public Map<String,Object> dashboard(User user) {
        return Map.of("user",user,"career",career(user.careerId),"skills",userSkills.findByUserIdOrderByIdAsc(user.id),
                "careers",careers.findAll(),"roadmap",generateRoadmap(user),"projects",projects.findByUserIdOrderByIdDesc(user.id),
                "resources",resources.findAll(),"gap",gap(user,user.careerId));
    }
    public User profile(User user,Profile input) {
        career(input.careerId());
        user.name=input.name().trim(); user.course=input.course(); user.college=input.college();
        user.currentYear=input.currentYear(); user.careerId=input.careerId();
        users.save(user); generateRoadmap(user); return user;
    }
    public UserSkill addSkill(User user,SkillInput input) {
        if(!Set.of("Beginner","Intermediate","Advanced").contains(input.level())) throw bad("Choose a valid skill level");
        Skill skill=skill(input.skillId());
        UserSkill item=userSkills.findByUserIdAndSkillId(user.id,skill.id).orElseGet(UserSkill::new);
        item.user=user; item.skill=skill; item.level=input.level();
        userSkills.save(item); generateRoadmap(user); return item;
    }
    public void removeSkill(User user,Long id) {
        UserSkill item=userSkills.findById(id).orElseThrow(()->missing("Skill not found"));
        owns(item.user.id,user); userSkills.delete(item); userSkills.flush(); generateRoadmap(user);
    }
    public RoadmapStep updateStep(User user,Long id,Status input) {
        if(!Set.of("NOT_STARTED","IN_PROGRESS","COMPLETED").contains(input.status())) throw bad("Invalid roadmap status");
        RoadmapStep item=roadmap.findById(id).orElseThrow(()->missing("Roadmap step not found"));
        owns(item.user.id,user);
        if(!item.careerId.equals(user.careerId)) throw bad("Select this step's career before updating it");
        item.status=input.status();
        if(item.skill!=null) {
            Optional<UserSkill> learned=userSkills.findByUserIdAndSkillId(user.id,item.skill.id);
            if("COMPLETED".equals(input.status()) && learned.isEmpty()) {
                UserSkill entry=new UserSkill(); entry.user=user; entry.skill=item.skill; entry.level="Beginner"; userSkills.save(entry);
            } else if(!"COMPLETED".equals(input.status()) && learned.isPresent()) {
                userSkills.delete(learned.get()); userSkills.flush();
            }
        }
        return roadmap.save(item);
    }
    public Project saveProject(User user,Long id,ProjectInput input) {
        if(!Set.of("IN_PROGRESS","COMPLETED").contains(input.status())) throw bad("Invalid project status");
        validateUrl(input.githubUrl(),true);
        Project item=id==null ? new Project() : projects.findById(id).orElseThrow(()->missing("Project not found"));
        if(item.user!=null) owns(item.user.id,user);
        item.user=user; item.name=input.name().trim(); item.description=input.description();
        item.technology=input.technology(); item.githubUrl=input.githubUrl(); item.status=input.status();
        return projects.save(item);
    }
    public void removeProject(User user,Long id) {
        Project item=projects.findById(id).orElseThrow(()->missing("Project not found"));
        owns(item.user.id,user); projects.delete(item);
    }
    public Career saveCareer(Long id,CareerInput input) {
        Career item=id==null ? new Career() : career(id);
        item.name=input.name().trim(); item.description=input.description(); item.skills.clear();
        input.skillIds().forEach(skillId->item.skills.add(skill(skillId)));
        return careers.save(item);
    }
    public void removeCareer(Long id) {
        career(id);
        if(users.findAll().stream().anyMatch(u->id.equals(u.careerId))) throw bad("Students are following this career. Reassign them before deleting it.");
        roadmap.deleteByCareerId(id); careers.deleteById(id);
    }
    public Resource saveResource(Long id,ResourceInput input) {
        validateUrl(input.url(),false);
        Resource item=id==null ? new Resource() : resources.findById(id).orElseThrow(()->missing("Resource not found"));
        item.skill=skill(input.skillId()); item.title=input.title().trim(); item.url=input.url(); item.type=input.type();
        return resources.save(item);
    }
    public void removeResource(Long id) {
        Resource item=resources.findById(id).orElseThrow(()->missing("Resource not found")); resources.delete(item);
    }
    private void validateUrl(String url,boolean optional) {
        if(optional && (url==null || url.isBlank())) return;
        try {
            URI parsed=URI.create(url);
            if(!Set.of("https","http").contains(parsed.getScheme()) || parsed.getHost()==null) throw bad("Enter a valid http or https URL");
        } catch(IllegalArgumentException|NullPointerException e) { throw bad("Enter a valid http or https URL"); }
    }
}
