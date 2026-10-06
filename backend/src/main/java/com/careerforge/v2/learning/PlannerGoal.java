package com.careerforge.v2.learning;

import com.careerforge.model.User;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name="planner_goals")
public class PlannerGoal {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="user_id",nullable=false) public User user;
    @Column(nullable=false) public String title;
    @Column(name="week_start",nullable=false) public LocalDate weekStart;
    @Column(name="target_minutes",nullable=false) public int targetMinutes;
}
