package com.careerforge.model;

import jakarta.persistence.*;

@Entity
@Table(name="resources")
public class Resource {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional=false,fetch=FetchType.EAGER) @JoinColumn(name="skill_id") public Skill skill;
    @Column(nullable=false) public String title;
    @Column(nullable=false,length=1000) public String url;
    public String type;
}
