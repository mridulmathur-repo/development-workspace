package com.example.googlesso.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String googleSubject;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String displayName;

    private String pictureUrl;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastLoginAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32) default 'INVENTORY_USER'")
    private UserRole role = UserRole.INVENTORY_USER;

    protected AppUser() {}

    public AppUser(String googleSubject, String email, String displayName,
                   String pictureUrl, Instant now) {
        this.googleSubject = googleSubject;
        this.email = email;
        this.displayName = displayName;
        this.pictureUrl = pictureUrl;
        this.createdAt = now;
        this.lastLoginAt = now;
    }

    public void recordLogin(String email, String displayName, String pictureUrl, Instant now) {
        this.email = email;
        this.displayName = displayName;
        this.pictureUrl = pictureUrl;
        this.lastLoginAt = now;
    }

    public void changeRole(UserRole role) {
        this.role = role;
    }

    public Long getId() { return id; }
    public String getGoogleSubject() { return googleSubject; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPictureUrl() { return pictureUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public UserRole getRole() { return role; }
}
