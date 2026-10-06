package com.careerforge.v2.security;

import com.careerforge.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="account_tokens")
public class RecoveryToken {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @JsonIgnore @ManyToOne(optional=false,fetch=FetchType.EAGER) @JoinColumn(name="user_id") public User user;
    @JsonIgnore @Column(nullable=false,unique=true,length=64) public String tokenHash;
    @Column(nullable=false,length=30) public String purpose;
    @Column(nullable=false) public Instant expiresAt;
    @Column(nullable=false) public boolean used;
    @Column(nullable=false) public Instant createdAt;
}
