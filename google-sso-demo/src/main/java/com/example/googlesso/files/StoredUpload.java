package com.example.googlesso.files;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "stored_uploads")
public class StoredUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String storedName;

    @Column(nullable = false, updatable = false)
    private String originalName;

    @Column(nullable = false, updatable = false)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private long size;

    @Column(nullable = false, updatable = false)
    private String ownerGoogleSubject;

    @Column(nullable = false, updatable = false)
    private String ownerEmail;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;

    protected StoredUpload() {}

    public StoredUpload(String storedName, String originalName, String contentType,
                        long size, String ownerGoogleSubject, String ownerEmail,
                        Instant uploadedAt) {
        this.storedName = storedName;
        this.originalName = originalName;
        this.contentType = contentType;
        this.size = size;
        this.ownerGoogleSubject = ownerGoogleSubject;
        this.ownerEmail = ownerEmail;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public String getStoredName() { return storedName; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }
    public String getOwnerGoogleSubject() { return ownerGoogleSubject; }
    public String getOwnerEmail() { return ownerEmail; }
    public Instant getUploadedAt() { return uploadedAt; }
}
