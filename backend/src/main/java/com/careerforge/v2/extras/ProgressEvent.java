package com.careerforge.v2.extras;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="progress_events", indexes=@Index(name="ix_progress_user_time", columnList="user_id,occurred_at,id"))
public class ProgressEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(name="user_id", nullable=false) Long userId;
    @Column(nullable=false, length=64) String type;
    @Column(nullable=false, length=255) String title;
    @Column(nullable=false) double readiness;
    @Column(name="occurred_at", nullable=false) Instant occurredAt;
    @Column(name="career_id") Long careerId;
}
