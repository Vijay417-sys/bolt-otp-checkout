package com.bolt.checkout.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Append-only record of a security-relevant event.
 *
 * <p>Deliberately has no foreign key to {@code users}: audit history has to
 * outlive the account it describes, so {@code userId} is a loose reference.
 *
 * <p>No OTP, password hash or session token is ever written here - {@code detail}
 * is a short human-readable reason, not a payload.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String event;

    @Column(nullable = false, length = 32)
    private String outcome;

    @Column(length = 255)
    private String email;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 500)
    private String detail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
