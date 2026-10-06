package com.careerforge.model;

import jakarta.persistence.*;

@Entity
@Table(name="roadmap_steps",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","career_id","title"}))
public class RoadmapStep {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @com.fasterxml.jackson.annotation.JsonIgnore @ManyToOne(optional=false) @JoinColumn(name="user_id") public User user;
    @Column(name="career_id",nullable=false) public Long careerId;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch=FetchType.EAGER)
    @JoinColumn(name="career_id",insertable=false,updatable=false)
    public Career career;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="skill_id") public Skill skill;
    @Column(nullable=false) public String title;
    @Column(nullable=false) public String status="NOT_STARTED";
    @Column(nullable=false) public int position;
}
