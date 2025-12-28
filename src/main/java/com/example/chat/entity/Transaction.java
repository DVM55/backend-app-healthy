package com.example.chat.entity;

import com.example.chat.enums.PaymentStatus;
import com.example.chat.enums.RefundStatus;
import com.example.chat.security.AesEncryptor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Convert(converter = AesEncryptor.class)
    @Column(name = "app_trans_id", length = 100)
    private String appTransId;

    @Convert(converter = AesEncryptor.class)
    @Column(name = "zp_trans_id", length = 100)
    private String zpTransId;

    @Convert(converter = AesEncryptor.class)
    @Column(name = "refund_id", length = 100)
    private String refundId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", length = 20, nullable = false)
    private RefundStatus refundStatus = RefundStatus.NONE;

    @Convert(converter = AesEncryptor.class)
    @Column(name = "zp_refund_id", length = 100)
    private String zpRefundId;

    @Column(name = "payment_date")
    private LocalDateTime paymentDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
