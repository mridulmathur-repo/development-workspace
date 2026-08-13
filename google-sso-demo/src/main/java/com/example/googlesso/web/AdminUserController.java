package com.example.googlesso.web;

import com.example.googlesso.service.CurrentUserService;
import com.example.googlesso.user.AppUser;
import com.example.googlesso.user.AppUserRepository;
import com.example.googlesso.user.UserRole;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@Controller
public class AdminUserController {
    private final AppUserRepository users;
    private final CurrentUserService currentUsers;

    public AdminUserController(AppUserRepository users, CurrentUserService currentUsers) {
        this.users = users;
        this.currentUsers = currentUsers;
    }

    @GetMapping("/admin/users")
    String users(@AuthenticationPrincipal OAuth2User principal, Model model) {
        currentUsers.requireAdmin(principal);
        model.addAttribute("users", users.findAll());
        model.addAttribute("roles", UserRole.values());
        return "admin-users";
    }

    @PostMapping("/admin/users/{id}/role")
    @Transactional
    String changeRole(@AuthenticationPrincipal OAuth2User principal,
                      @PathVariable Long id,
                      @RequestParam UserRole role) {
        AppUser actingAdmin = currentUsers.requireAdmin(principal);
        AppUser target = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (target.getId().equals(actingAdmin.getId())
                && role != UserRole.ADMIN
                && users.countByRole(UserRole.ADMIN) == 1) {
            return "redirect:/admin/users?error=last_admin";
        }
        target.changeRole(role);
        users.save(target);
        return "redirect:/admin/users?updated";
    }
}
