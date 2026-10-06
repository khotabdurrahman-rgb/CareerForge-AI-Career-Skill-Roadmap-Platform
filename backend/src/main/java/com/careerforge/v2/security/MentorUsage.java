package com.careerforge.v2.security;

import com.careerforge.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name="mentor_usage",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","usage_date"}))
public class MentorUsage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @JsonIgnore @ManyToOne(optional=false,fetch=FetchType.EAGER) @JoinColumn(name="user_id") public User user;
    @Column(nullable=false) public LocalDate usageDate;
    @Column(nullable=false) public int requestCount;
}
