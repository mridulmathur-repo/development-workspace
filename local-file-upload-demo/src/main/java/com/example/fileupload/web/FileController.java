package com.example.fileupload.web;

import com.example.fileupload.storage.LocalFileStorageService;
import com.example.fileupload.storage.StoredFile;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FileController {
    private final LocalFileStorageService storage;

    public FileController(LocalFileStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/")
    String page(Model model) {
        model.addAttribute("files", storage.list());
        model.addAttribute("storageLocation", storage.storageLocation());
        return "files";
    }

    @PostMapping("/files")
    String upload(@RequestParam("file") MultipartFile file,
                  RedirectAttributes redirectAttributes) {
        StoredFile stored = storage.store(file);
        redirectAttributes.addFlashAttribute(
                "message", "Uploaded " + stored.displayName() + " successfully.");
        return "redirect:/";
    }

    @GetMapping("/files/{storedName:.+}")
    ResponseEntity<Resource> download(@PathVariable String storedName) {
        Resource resource = storage.load(storedName);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(storage.displayName(storedName), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }
}
