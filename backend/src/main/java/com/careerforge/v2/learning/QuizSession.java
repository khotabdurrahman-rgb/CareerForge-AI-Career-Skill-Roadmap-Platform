package com.careerforge.v2.learning;

import com.careerforge.model.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="quiz_sessions")
public class QuizSession {
    @Id @Column(length=36) public String id;
    @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="user_id",nullable=false) public User user;
    @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="skill_id",nullable=false) public Skill skill;
    @Lob @Column(name="question_set",nullable=false,length=2147483647) public String questionSet;
    @Column(name="expires_at",nullable=false) public Instant expiresAt;
    @Column(nullable=false) public boolean submitted;
}
