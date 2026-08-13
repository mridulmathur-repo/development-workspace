package com.example.googlesso.service;

import com.example.googlesso.user.AppUser;
import com.example.googlesso.user.AppUserRepository;
import com.example.googlesso.user.UserRole;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GoogleAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    public static final String AUTH_MODE = "GOOGLE_AUTH_MODE";

    private final AppUserRepository users;
    private final String bootstrapAdminEmail;

    public GoogleAuthenticationSuccessHandler(
            AppUserRepository users,
            @Value("${app.bootstrap-admin-email:}") String bootstrapAdminEmail) {
        this.users = users;
        this.bootstrapAdminEmail = bootstrapAdminEmail;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {
        OAuth2User googleUser = (OAuth2User) authentication.getPrincipal();
        String subject = required(googleUser, "sub");
        String email = required(googleUser, "email");
        String name = required(googleUser, "name");
        String picture = googleUser.getAttribute("picture");
        String mode = (String) request.getSession().getAttribute(AUTH_MODE);
        request.getSession().removeAttribute(AUTH_MODE);

        AppUser existing = users.findByGoogleSubject(subject).orElse(null);
        if ("signup".equals(mode)) {
            AppUser user = existing != null
                    ? existing
                    : new AppUser(subject, email, name, picture, Instant.now());
            applyBootstrapAdmin(user, email);
            user.recordLogin(email, name, picture, Instant.now());
            users.save(user);
            response.sendRedirect("/home?created=" + (existing == null));
            return;
        }

        if (existing == null) {
            request.getSession().invalidate();
            response.sendRedirect("/login?error=account_not_found");
            return;
        }

        applyBootstrapAdmin(existing, email);
        existing.recordLogin(email, name, picture, Instant.now());
        users.save(existing);
        response.sendRedirect("/home");
    }

    private void applyBootstrapAdmin(AppUser user, String googleEmail) {
        if (!bootstrapAdminEmail.isBlank()
                && googleEmail.equalsIgnoreCase(bootstrapAdminEmail.trim())) {
            user.changeRole(UserRole.ADMIN);
        }
    }

    private String required(OAuth2User user, String attribute) throws ServletException {
        String value = user.getAttribute(attribute);
        if (value == null || value.isBlank()) {
            throw new ServletException("Google did not return required attribute: " + attribute);
        }
        return value;
    }
}
