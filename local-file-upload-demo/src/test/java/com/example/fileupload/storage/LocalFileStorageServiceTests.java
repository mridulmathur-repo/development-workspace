package com.example.fileupload.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalFileStorageServiceTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesAndListsAFile() throws Exception {
        LocalFileStorageService service = service();
        MockMultipartFile upload = new MockMultipartFile(
                "file", "report.txt", "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8));

        StoredFile stored = service.store(upload);

        assertThat(stored.displayName()).isEqualTo("report.txt");
        assertThat(service.list()).extracting(StoredFile::displayName)
                .containsExactly("report.txt");
        assertThat(service.load(stored.storedName()).getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("hello");
    }

    @Test
    void createsDifferentStoredNamesForDuplicateOriginalNames() {
        LocalFileStorageService service = service();
        MockMultipartFile first = new MockMultipartFile("file", "same.txt", "text/plain", new byte[]{1});
        MockMultipartFile second = new MockMultipartFile("file", "same.txt", "text/plain", new byte[]{2});

        assertThat(service.store(first).storedName())
                .isNotEqualTo(service.store(second).storedName());
        assertThat(service.list()).hasSize(2);
    }

    @Test
    void rejectsEmptyUpload() {
        LocalFileStorageService service = service();
        MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.store(empty))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("non-empty");
    }

    @Test
    void rejectsTraversalWhenLoading() {
        assertThatThrownBy(() -> service().load("../secret.txt"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("Invalid");
    }

    private LocalFileStorageService service() {
        return new LocalFileStorageService(new StorageProperties(temporaryDirectory));
    }
}
