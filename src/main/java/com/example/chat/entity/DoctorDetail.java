package com.example.chat.entity;

import com.example.chat.enums.Gender;
import com.example.chat.security.AesEncryptor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "doctor_details")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = AesEncryptor.class)
    @Column(name = "specialization", length = 100)
    private String specialization;

    @Column(name = "experience_years")
    @Builder.Default
    private Integer experienceYears = 0;

    @Convert(converter = AesEncryptor.class)
    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate date_of_birth;

    @Convert(converter = AesEncryptor.class)
    @Column(name = "avatar_url")
    private String avatar_url;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Convert(converter = AesEncryptor.class)
    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "fee", nullable = false)
    @Builder.Default
    private Long fee = 0L;

    @OneToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "user_id", referencedColumnName = "id", unique = true)
    private Account account;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
