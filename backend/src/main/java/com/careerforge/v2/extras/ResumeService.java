package com.careerforge.v2.extras;

import com.careerforge.model.*;
import com.careerforge.service.CareerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.*;
import static com.careerforge.v2.extras.ExtrasDtos.*;

@Service
public class ResumeService {
    private final CareerService data;
    private final ResumeProfileRepository profiles;
    private final ObjectMapper json;
    private final EntityManager em;
    public ResumeService(CareerService data,ResumeProfileRepository profiles,ObjectMapper json,EntityManager em) {
        this.data=data; this.profiles=profiles; this.json=json; this.em=em;
    }
    @Transactional(readOnly=true)
    public ResumeBundle get(User user) { return bundle(owner(user),null); }

    @Transactional
    public ResumeBundle put(User user,ResumeFields fields) {
        User owner=(User)Hibernate.unproxy(em.find(User.class,em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(user),LockModeType.PESSIMISTIC_WRITE));
        validateWebsite(fields.website());
        ResumeProfile profile=profiles.findByUserId(owner.id).orElseGet(ResumeProfile::new);
        profile.userId=owner.id;
        try { profile.fieldsJson=json.writeValueAsString(fields); }
        catch(JsonProcessingException ex) { throw new IllegalStateException("Cannot encode resume",ex); }
        if(profile.fieldsJson.length()>20000) throw CareerService.bad("Resume content is too long");
        profiles.save(profile);
        return bundle(owner,fields);
    }
    private User owner(User user) {
        Long id=(Long)em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(user);
        return (User)Hibernate.unproxy(data.user(id));
    }
    private ResumeBundle bundle(User user,ResumeFields input) {
        ResumeFields fields=input;
        if(fields==null) {
            Optional<ResumeProfile> profile=profiles.findByUserId(user.id);
            if(profile.isPresent()) {
                try { fields=json.readValue(profile.get().fieldsJson,ResumeFields.class); }
                catch(JsonProcessingException ex) { throw new IllegalStateException("Cannot decode resume",ex); }
            } else fields=new ResumeFields("","","","","",education(user),"","",true,true,true,true,true);
        }
        List<LearnedSkill> skills=data.userSkills().findByUserIdOrderByIdAsc(user.id).stream().map(item->{
            UserSkill s=(UserSkill)Hibernate.unproxy(item);
            return new LearnedSkill(s.id,ExtrasViews.skill(s.skill),s.level);
        }).toList();
        List<ProjectView> projects=data.projects().findByUserIdOrderByIdDesc(user.id).stream().map(ExtrasViews::project).toList();
        return new ResumeBundle(fields,new ResumeUser(user.name,user.email,user.course,user.college,user.currentYear),skills,projects);
    }
    private String education(User u) {
        return java.util.stream.Stream.of(u.course,u.college,u.currentYear).filter(s->s!=null && !s.isBlank()).collect(java.util.stream.Collectors.joining(" | "));
    }
    private void validateWebsite(String website) {
        if(website==null || website.isBlank()) return;
        try {
            URI uri=URI.create(website);
            if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException();
        } catch(IllegalArgumentException ex) { throw CareerService.bad("Enter a valid http or https website URL"); }
    }
}
