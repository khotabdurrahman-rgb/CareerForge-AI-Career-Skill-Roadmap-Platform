package com.careerforge.model;

import jakarta.persistence.*;

@Entity
@Table(name="careers")
public class Career {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false,unique=true) public String name;
    @Column(length=2000) public String description;
    @ManyToMany(fetch=FetchType.EAGER)
    @JoinTable(name="career_skills",joinColumns=@JoinColumn(name="career_id"),inverseJoinColumns=@JoinColumn(name="skill_id"))
    @OrderBy("id ASC") public java.util.Set<Skill> skills=new java.util.LinkedHashSet<>();
}
