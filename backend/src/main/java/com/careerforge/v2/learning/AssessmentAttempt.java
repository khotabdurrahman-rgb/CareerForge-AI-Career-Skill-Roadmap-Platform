package com.careerforge.v2.learning;

import com.careerforge.model.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="assessment_attempts",uniqueConstraints=@UniqueConstraint(columnNames="session_id"))
public class AssessmentAttempt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="user_id",nullable=false) public User user;
    @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="skill_id",nullable=false) public Skill skill;
    @Column(name="session_id",nullable=false,length=36) public String sessionId;
    @Column(nullable=false) public double score;
    @Column(nullable=false) public int correct;
    @Column(nullable=false) public int total;
    @Column(name="completed_at",nullable=false) public Instant completedAt;
}
