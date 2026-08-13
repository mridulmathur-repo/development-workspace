package com.example.googlesso.files;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredUploadRepository extends JpaRepository<StoredUpload, Long> {
    List<StoredUpload> findAllByOrderByUploadedAtDesc();
    List<StoredUpload> findByOwnerGoogleSubjectOrderByUploadedAtDesc(String subject);
    Optional<StoredUpload> findByStoredName(String storedName);
}
