package com.demomart.identity.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_accounts", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String email;
    @Column(nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private UserRole role;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String postalCode;
    private String country;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    protected UserAccount() {}

    public UserAccount(String name, String email, String passwordHash, UserRole role) {
        this.name = name;
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
    public Instant getCreatedAt() { return createdAt; }
    public void setRole(UserRole role) { this.role = role; }
    public void updateAddress(String line1, String line2, String city, String postalCode, String country) {
        this.addressLine1 = line1; this.addressLine2 = line2; this.city = city;
        this.postalCode = postalCode; this.country = country;
    }
}

