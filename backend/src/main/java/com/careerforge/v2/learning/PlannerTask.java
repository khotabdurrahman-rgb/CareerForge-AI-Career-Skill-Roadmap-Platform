package com.careerforge.v2.learning;

import com.careerforge.model.*;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name="planner_tasks")
public class PlannerTask {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="user_id",nullable=false) public User user;
    @Column(nullable=false) public String title;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="skill_id") public Skill skill;
    @Column(name="roadmap_step_id") public Long roadmapStepId;
    @Column(name="due_date",nullable=false) public LocalDate dueDate;
    @Column(name="estimated_minutes",nullable=false) public int estimatedMinutes;
    @Column(nullable=false,length=20) public String status;
    @Column(name="week_start",nullable=false) public LocalDate weekStart;
}
