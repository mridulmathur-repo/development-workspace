package com.example.googlesso.files;

import com.example.googlesso.user.AppUser;
import com.example.googlesso.user.UserRole;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SecureFileStorageService {
    private final Path root;
    private final StoredUploadRepository uploads;

    public SecureFileStorageService(
            @Value("${app.file-storage-location:./uploads}") Path location,
            StoredUploadRepository uploads) {
        this.root = location.toAbsolutePath().normalize();
        this.uploads = uploads;
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new FileStorageException("Could not create the upload directory.", exception);
        }
    }

    @Transactional
    public StoredUpload store(MultipartFile file, AppUser owner) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Please choose a non-empty file.");
        }
        String cleaned = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String originalName = Path.of(cleaned).getFileName().toString();
        if (originalName.isBlank() || originalName.equals(".") || originalName.equals("..")) {
            throw new FileStorageException("The supplied filename is invalid.");
        }

        String storedName = UUID.randomUUID().toString();
        Path destination = root.resolve(storedName).normalize();
        if (!destination.startsWith(root)) {
            throw new FileStorageException("Invalid storage path.");
        }

        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination);
            StoredUpload upload = new StoredUpload(
                    storedName,
                    originalName,
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                    Files.size(destination),
                    owner.getGoogleSubject(),
                    owner.getEmail(),
                    Instant.now());
            return uploads.save(upload);
        } catch (IOException exception) {
            try { Files.deleteIfExists(destination); } catch (IOException ignored) {}
            throw new FileStorageException("Could not store the uploaded file.", exception);
        }
    }

    public List<StoredUpload> visibleTo(AppUser user) {
        return user.getRole() == UserRole.ADMIN
                ? uploads.findAllByOrderByUploadedAtDesc()
                : uploads.findByOwnerGoogleSubjectOrderByUploadedAtDesc(user.getGoogleSubject());
    }

    public DownloadedUpload load(Long id, AppUser user) {
        StoredUpload upload = uploads.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean ownsFile = upload.getOwnerGoogleSubject().equals(user.getGoogleSubject());
        if (!ownsFile && user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        try {
            Path path = root.resolve(upload.getStoredName()).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            Resource resource = new UrlResource(path.toUri());
            return new DownloadedUpload(upload, resource);
        } catch (IOException exception) {
            throw new FileStorageException("Could not load the file.", exception);
        }
    }

    public record DownloadedUpload(StoredUpload metadata, Resource resource) {}
}
