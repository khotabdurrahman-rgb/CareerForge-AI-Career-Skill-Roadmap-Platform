package com.careerforge.v2.extras;

import com.careerforge.model.*;
import com.careerforge.service.CareerService;
import org.hibernate.Hibernate;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.careerforge.v2.extras.ExtrasDtos.*;

@Service
public class ComparisonService {
    private final CareerService data;
    private final EntityManager em;
    public ComparisonService(CareerService data,EntityManager em) { this.data=data; this.em=em; }
    @Transactional(readOnly=true)
    public Comparison compare(User user,Long leftId,Long rightId) {
        if(leftId<=0 || rightId<=0) throw CareerService.bad("Career IDs must be positive");
        Long ownerId=(Long)em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(user);
        User owner=(User)Hibernate.unproxy(data.user(ownerId));
        ComparisonSide left=side(owner,leftId), right=side(owner,rightId);
        Set<Long> rightSkills=new HashSet<>();
        right.career().skills().forEach(s->rightSkills.add(s.id()));
        return new Comparison(left,right,left.career().skills().stream().filter(s->rightSkills.contains(s.id())).toList(),
            "20 learning hours per missing required skill; planning estimate only, excluding capstone work. "
            +"Readiness reuses V1 declared learning coverage, not assessed proficiency. Shared skills are all required skills common to both careers.");
    }
    @SuppressWarnings("unchecked")
    private ComparisonSide side(User user,Long id) {
        Career career=(Career)Hibernate.unproxy(data.career(id));
        Map<String,Object> gap=data.gap(user,id);
        List<SkillView> completed=((List<Skill>)gap.get("completed")).stream().map(ExtrasViews::skill).toList();
        List<SkillView> missing=((List<Skill>)gap.get("missing")).stream().map(ExtrasViews::skill).toList();
        return new ComparisonSide(new CareerView(career.id,career.name,career.description,career.skills.stream().map(ExtrasViews::skill).toList()),
            new GapView(completed,missing,((Number)gap.get("readiness")).doubleValue()),missing.size()*20);
    }
}
