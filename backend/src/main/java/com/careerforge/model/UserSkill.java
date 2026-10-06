package com.careerforge.model;

import jakarta.persistence.*;

@Entity
@Table(name="user_skills",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","skill_id"}))
public class UserSkill {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @com.fasterxml.jackson.annotation.JsonIgnore @ManyToOne(optional=false) @JoinColumn(name="user_id") public User user;
    @ManyToOne(optional=false,fetch=FetchType.EAGER) @JoinColumn(name="skill_id") public Skill skill;
    @Column(nullable=false) public String level;
}
