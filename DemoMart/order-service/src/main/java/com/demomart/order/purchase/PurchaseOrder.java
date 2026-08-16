package com.demomart.order.purchase;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private String userEmail;
    @Column(nullable = false) private Long phoneId;
    @Column(nullable = false) private String phoneName;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal totalAmount;
    @Column(nullable = false) private String addressLine1;
    private String addressLine2;
    @Column(nullable = false) private String city;
    @Column(nullable = false) private String postalCode;
    @Column(nullable = false) private String country;
    @Column(nullable = false) private String paymentMethod;
    @Column(nullable = false) private String status;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    protected PurchaseOrder() {}
    public PurchaseOrder(Long userId, String userEmail, Long phoneId, String phoneName, BigDecimal totalAmount,
                         String line1, String line2, String city, String postalCode, String country) {
        this.userId = userId; this.userEmail = userEmail; this.phoneId = phoneId; this.phoneName = phoneName;
        this.totalAmount = totalAmount; this.addressLine1 = line1; this.addressLine2 = line2;
        this.city = city; this.postalCode = postalCode; this.country = country;
        this.paymentMethod = "CASH_ON_DELIVERY"; this.status = "PLACED"; this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getUserEmail() { return userEmail; }
    public Long getPhoneId() { return phoneId; }
    public String getPhoneName() { return phoneName; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}

