package com.careerforge.model;

import jakarta.persistence.*;

@Entity
@Table(name="projects")
public class Project {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @com.fasterxml.jackson.annotation.JsonIgnore @ManyToOne(optional=false) @JoinColumn(name="user_id") public User user;
    @Column(nullable=false) public String name;
    @Column(length=2000) public String description;
    public String technology;
    @Column(length=1000) public String githubUrl;
    public String status;
}
