package com.demomart.catalog.phone;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "phones")
public class Phone {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String brand;
    @Column(nullable = false) private String model;
    @Column(nullable = false, length = 1000) private String description;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
    @Column(nullable = false) private int stock;
    private String color;
    private String storage;

    protected Phone() {}
    public Phone(String brand, String model, String description, BigDecimal price, int stock, String color, String storage) {
        this.brand = brand; this.model = model; this.description = description; this.price = price;
        this.stock = stock; this.color = color; this.storage = storage;
    }
    public Long getId() { return id; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public int getStock() { return stock; }
    public String getColor() { return color; }
    public String getStorage() { return storage; }
    public void reserveOne() { if (stock <= 0) throw new IllegalStateException("Phone is out of stock"); stock--; }
}

