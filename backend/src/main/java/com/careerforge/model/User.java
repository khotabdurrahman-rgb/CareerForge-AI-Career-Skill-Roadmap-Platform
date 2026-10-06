package com.careerforge.model;

import jakarta.persistence.*;

@Entity
@Table(name="users")
public class User {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public String name;
    @Column(nullable=false,unique=true) public String email;
    @com.fasterxml.jackson.annotation.JsonIgnore @Column(nullable=false) public String passwordHash;
    public String course;
    public String college;
    public String currentYear;
    @Column(name="career_id") public Long careerId;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch=FetchType.EAGER)
    @JoinColumn(name="career_id",insertable=false,updatable=false)
    public Career career;
    @Column(nullable=false) public String role="STUDENT";
}
