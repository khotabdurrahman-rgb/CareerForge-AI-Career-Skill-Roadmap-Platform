package com.careerforge.v2.extras;

import jakarta.persistence.*;

@Entity
@Table(name="resume_profiles", uniqueConstraints=@UniqueConstraint(name="uk_resume_user", columnNames="user_id"))
public class ResumeProfile {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(name="user_id", nullable=false) Long userId;
    @Lob @Column(name="fields_json", nullable=false,length=2147483647) String fieldsJson;
}
