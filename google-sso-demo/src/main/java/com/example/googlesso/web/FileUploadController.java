package com.example.googlesso.web;

import com.example.googlesso.files.SecureFileStorageService;
import com.example.googlesso.files.SecureFileStorageService.DownloadedUpload;
import com.example.googlesso.service.CurrentUserService;
import com.example.googlesso.user.AppUser;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FileUploadController {
    private final SecureFileStorageService storage;
    private final CurrentUserService currentUsers;

    public FileUploadController(SecureFileStorageService storage,
                                CurrentUserService currentUsers) {
        this.storage = storage;
        this.currentUsers = currentUsers;
    }

    @GetMapping("/files")
    String files(@AuthenticationPrincipal OAuth2User principal, Model model) {
        AppUser user = currentUsers.requireUser(principal);
        model.addAttribute("user", user);
        model.addAttribute("files", storage.visibleTo(user));
        return "files";
    }

    @PostMapping("/files")
    String upload(@AuthenticationPrincipal OAuth2User principal,
                  @RequestParam MultipartFile file,
                  RedirectAttributes redirectAttributes) {
        AppUser user = currentUsers.requireUser(principal);
        storage.store(file, user);
        redirectAttributes.addFlashAttribute("message", "File uploaded successfully.");
        return "redirect:/files";
    }

    @GetMapping("/files/{id}/download")
    ResponseEntity<Resource> download(@AuthenticationPrincipal OAuth2User principal,
                                      @PathVariable Long id) {
        AppUser user = currentUsers.requireUser(principal);
        DownloadedUpload download = storage.load(id, user);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.metadata().getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }
}
