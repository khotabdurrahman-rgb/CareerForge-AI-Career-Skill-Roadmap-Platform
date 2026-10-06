package com.careerforge.v2.extras;

import com.careerforge.model.*;
import com.careerforge.dto.Requests.ProjectInput;
import com.careerforge.service.CareerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.hibernate.Hibernate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.util.*;
import static com.careerforge.v2.extras.ExtrasDtos.*;

@Service
public class RecommendationService {
    public record Template(String id,String title,String description,String difficulty,List<String> technologies,
        List<String> prerequisites,List<String> milestones,List<String> careerNames) {}
    private final List<Template> catalog;
    private final CareerService data;
    private final SavedRecommendationRepository saved;
    private final EntityManager em;
    public RecommendationService(CareerService data,SavedRecommendationRepository saved,EntityManager em,ObjectMapper json) {
        this.data=data; this.saved=saved; this.em=em;
        try(InputStream input=new ClassPathResource("recommendations.json").getInputStream()) {
            catalog=List.copyOf(json.readValue(input,new TypeReference<List<Template>>() {}));
        } catch(IOException ex) { throw new IllegalStateException("Cannot load project recommendations",ex); }
        Set<String> ids=new HashSet<>();
        for(Template t:catalog) {
            if(t.id()==null || !t.id().matches("[a-z0-9]+(?:-[a-z0-9]+)*") || !ids.add(t.id())) throw new IllegalStateException("Invalid recommendation ID");
        }
    }
    @Transactional(readOnly=true)
    public List<Recommendation> list(User user) {
        User owner=owner(user,false);
        List<Career> careers=data.careers().findAll();
        Set<String> known=new HashSet<>();
        data.userSkills().findByUserIdOrderByIdAsc(owner.id).forEach(s->known.add(ExtrasViews.skill(s.skill).name().toLowerCase(Locale.ROOT)));
        Map<String,SavedRecommendation> bookmarks=new HashMap<>();
        saved.findByUserId(owner.id).forEach(s->bookmarks.put(s.recommendationId,s));
        return catalog.stream().filter(t->careerIds(t,careers).contains(owner.careerId))
            .filter(t->t.prerequisites().stream().allMatch(p->known.contains(p.toLowerCase(Locale.ROOT))))
            .sorted(Comparator.<Template>comparingLong(t->t.technologies().stream().filter(s->known.contains(s.toLowerCase(Locale.ROOT))).count()).reversed()
                .thenComparingInt(t->"Beginner".equals(t.difficulty()) ? 0 : 1).thenComparing(Template::id))
            .map(t->view(t,careerIds(t,careers),bookmarks.get(t.id()))).toList();
    }
    @Transactional
    public Recommendation bookmark(User user,String id,boolean value) {
        Template t=template(id);
        User owner=owner(user,true);
        SavedRecommendation state=state(owner.id,id);
        state.saved=value;
        saved.save(state);
        return view(t,careerIds(t,data.careers().findAll()),state);
    }
    @Transactional
    public ProjectView portfolio(User user,String id) {
        Template t=template(id);
        User owner=owner(user,true);
        SavedRecommendation state=state(owner.id,id);
        if(state.addedProjectId!=null) {
            return data.projects().findByUserIdOrderByIdDesc(owner.id).stream()
                .map(ExtrasViews::project).filter(p->p.id().equals(state.addedProjectId)).findFirst()
                .orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "The previously added project was deleted; this recommendation cannot create another project"));
        }
        Project project=data.saveProject(owner,null,new ProjectInput(t.title(),t.description(),String.join(", ",t.technologies()),"","IN_PROGRESS"));
        state.addedProjectId=project.id;
        saved.save(state);
        return ExtrasViews.project(project);
    }
    private User owner(User user,boolean lock) {
        Long id=(Long)em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(user);
        User owner=lock ? em.find(User.class,id,LockModeType.PESSIMISTIC_WRITE) : data.user(id);
        return (User)Hibernate.unproxy(owner);
    }
    private SavedRecommendation state(Long userId,String id) {
        return saved.findByUserIdAndRecommendationId(userId,id).orElseGet(()->{
            SavedRecommendation s=new SavedRecommendation(); s.userId=userId; s.recommendationId=id; return s;
        });
    }
    private Template template(String id) { return catalog.stream().filter(t->t.id().equals(id)).findFirst().orElseThrow(()->CareerService.missing("Recommendation not found")); }
    private List<Long> careerIds(Template t,List<Career> careers) {
        return careers.stream().map(c->(Career)Hibernate.unproxy(c)).filter(c->t.careerNames().contains(c.name)).map(c->c.id).sorted().toList();
    }
    private Recommendation view(Template t,List<Long> careerIds,SavedRecommendation state) {
        return new Recommendation(t.id(),t.title(),t.description(),t.difficulty(),t.technologies(),t.prerequisites(),t.milestones(),careerIds,
            state!=null && state.saved,state==null ? null : state.addedProjectId);
    }
}
