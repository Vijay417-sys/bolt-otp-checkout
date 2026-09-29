package com.bolt.checkout.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(name = "otp_expires_at", nullable = false)
    private LocalDateTime otpExpiresAt;

    @Column(name = "otp_failed_attempts", nullable = false)
    private int otpFailedAttempts;

    @Column(name = "otp_locked_until")
    private LocalDateTime otpLockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * True while a lockout window is still in effect.
     */
    public boolean isOtpLocked(LocalDateTime now) {
        return otpLockedUntil != null && otpLockedUntil.isAfter(now);
    }

    /**
     * True once the current code has passed its expiry instant.
     *
     * <p>A null expiry counts as expired on purpose. Callers must set {@code otpExpiresAt}
     * explicitly, and if one ever is not, the code is treated as dead rather than valid
     * forever - the column is NOT NULL, so the database rejects that case too.
     */
    public boolean isOtpExpired(LocalDateTime now) {
        return otpExpiresAt == null || !otpExpiresAt.isAfter(now);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
    public LocalDateTime getOtpExpiresAt() { return otpExpiresAt; }
    public void setOtpExpiresAt(LocalDateTime otpExpiresAt) { this.otpExpiresAt = otpExpiresAt; }
    public int getOtpFailedAttempts() { return otpFailedAttempts; }
    public void setOtpFailedAttempts(int otpFailedAttempts) { this.otpFailedAttempts = otpFailedAttempts; }
    public LocalDateTime getOtpLockedUntil() { return otpLockedUntil; }
    public void setOtpLockedUntil(LocalDateTime otpLockedUntil) { this.otpLockedUntil = otpLockedUntil; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}