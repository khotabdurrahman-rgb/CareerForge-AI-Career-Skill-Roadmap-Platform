package com.careerforge.v2.security;

import com.careerforge.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="mentor_messages")
public class ChatMessage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @JsonIgnore @ManyToOne(optional=false,fetch=FetchType.EAGER) @JoinColumn(name="user_id") public User user;
    @Column(nullable=false,length=20) public String role;
    @Column(nullable=false,length=12000) public String content;
    @Column(nullable=false) public Instant createdAt;
}
