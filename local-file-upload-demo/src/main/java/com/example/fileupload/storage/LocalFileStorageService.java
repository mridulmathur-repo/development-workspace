package com.example.fileupload.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileStorageService {
    private final Path root;

    public LocalFileStorageService(StorageProperties properties) {
        this.root = properties.location().toAbsolutePath().normalize();
        try {
            System.out.println("Test");
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new FileStorageException("Could not create upload directory: " + root, ex);
        }
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Please select a non-empty file.");
        }

        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String displayName = Path.of(originalName).getFileName().toString();
        if (displayName.isBlank() || displayName.equals(".") || displayName.equals("..")) {
            throw new FileStorageException("The supplied filename is invalid.");
        }

        String storedName = UUID.randomUUID() + "--" + displayName;
        Path destination = resolveStoredName(storedName);
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination);
            return new StoredFile(storedName, displayName, Files.size(destination));
        } catch (IOException ex) {
            throw new FileStorageException("Could not store " + displayName + ".", ex);
        }
    }

    public List<StoredFile> list() {
        try (Stream<Path> paths = Files.list(root)) {
            return paths.filter(Files::isRegularFile)
                    .map(this::toStoredFile)
                    .sorted(Comparator.comparing(StoredFile::displayName,
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException ex) {
            throw new FileStorageException("Could not list stored files.", ex);
        }
    }

    public Resource load(String storedName) {
        try {
            Path file = resolveStoredName(storedName);
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable() || !Files.isRegularFile(file)) {
                throw new FileStorageException("File not found.");
            }
            return resource;
        } catch (IOException ex) {
            throw new FileStorageException("Could not read the requested file.", ex);
        }
    }

    public String displayName(String storedName) {
        int separator = storedName.indexOf("--");
        return separator >= 0 ? storedName.substring(separator + 2) : storedName;
    }

    public Path storageLocation() {
        return root;
    }

    private Path resolveStoredName(String storedName) {
        if (storedName == null || storedName.isBlank()
                || !Path.of(storedName).getFileName().toString().equals(storedName)) {
            throw new FileStorageException("Invalid stored filename.");
        }
        Path resolved = root.resolve(storedName).normalize();
        if (!resolved.startsWith(root)) {
            throw new FileStorageException("Files can only be accessed inside the upload directory.");
        }
        return resolved;
    }

    private StoredFile toStoredFile(Path path) {
        try {
            String storedName = path.getFileName().toString();
            return new StoredFile(storedName, displayName(storedName), Files.size(path));
        } catch (IOException ex) {
            throw new FileStorageException("Could not inspect a stored file.", ex);
        }
    }
}
