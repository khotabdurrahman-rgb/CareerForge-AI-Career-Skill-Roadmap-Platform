package com.careerforge.v2.extras;

import com.careerforge.model.*;
import org.hibernate.Hibernate;
import static com.careerforge.v2.extras.ExtrasDtos.*;

final class ExtrasViews {
    private ExtrasViews() {}
    static SkillView skill(Skill input) { Skill s=(Skill)Hibernate.unproxy(input); return new SkillView(s.id,s.name); }
    static ProjectView project(Project input) { Project p=(Project)Hibernate.unproxy(input); return new ProjectView(p.id,p.name,p.description,p.technology,p.githubUrl,p.status); }
}
