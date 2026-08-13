package com.example.googlesso.service;

import com.example.googlesso.user.AppUser;
import com.example.googlesso.user.AppUserRepository;
import com.example.googlesso.user.UserRole;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final AppUserRepository users;

    public CurrentUserService(AppUserRepository users) {
        this.users = users;
    }

    public AppUser requireUser(OAuth2User principal) {
        return users.findByGoogleSubject(principal.getAttribute("sub"))
                .orElseThrow(() -> new IllegalStateException("Authenticated user is missing locally"));
    }

    public AppUser requireAdmin(OAuth2User principal) {
        AppUser user = requireUser(principal);
        if (user.getRole() != UserRole.ADMIN) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Administrator access required");
        }
        return user;
    }
}
