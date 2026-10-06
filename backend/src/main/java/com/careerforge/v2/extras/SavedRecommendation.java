package com.careerforge.v2.extras;

import jakarta.persistence.*;

@Entity
@Table(name="saved_recommendations", uniqueConstraints=@UniqueConstraint(name="uk_recommendation_user", columnNames={"user_id","recommendation_id"}))
public class SavedRecommendation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(name="user_id", nullable=false) Long userId;
    @Column(name="recommendation_id", nullable=false, length=100) String recommendationId;
    @Column(nullable=false) boolean saved;
    @Column(name="added_project_id") Long addedProjectId;
}
