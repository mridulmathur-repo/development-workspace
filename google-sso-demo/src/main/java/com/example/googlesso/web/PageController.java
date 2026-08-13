package com.example.googlesso.web;

import com.example.googlesso.user.AppUser;
import com.example.googlesso.service.GoogleAuthenticationSuccessHandler;
import com.example.googlesso.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    private final CurrentUserService currentUsers;

    public PageController(CurrentUserService currentUsers) {
        this.currentUsers = currentUsers;
    }

    @GetMapping("/")
    String home() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/auth/google/{mode}")
    String beginGoogleAuthentication(@org.springframework.web.bind.annotation.PathVariable String mode,
                                     HttpSession session) {
        if (!mode.equals("login") && !mode.equals("signup")) {
            return "redirect:/login?error=invalid_mode";
        }
        session.setAttribute(GoogleAuthenticationSuccessHandler.AUTH_MODE, mode);
        return "redirect:/oauth2/authorization/google";
    }

    @GetMapping("/home")
    String authenticatedHome(@AuthenticationPrincipal OAuth2User principal,
                             HttpSession session, Model model) {
        AppUser user = currentUsers.requireUser(principal);
        model.addAttribute("user", user);
        model.addAttribute("isAdmin", user.getRole() == com.example.googlesso.user.UserRole.ADMIN);
        model.addAttribute("sessionId", session.getId());
        return "home";
    }
}
