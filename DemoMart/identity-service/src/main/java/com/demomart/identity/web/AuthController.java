package com.demomart.identity.web;

import com.demomart.identity.security.TokenService;
import com.demomart.identity.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokens;

    public AuthController(UserAccountRepository users, PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager, TokenService tokens) {
        this.users = users; this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager; this.tokens = tokens;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this email");
        UserAccount user = users.save(new UserAccount(request.name().trim(), request.email().trim(),
                passwordEncoder.encode(request.password()), UserRole.END_USER));
        return AuthResponse.from(user, tokens.issue(user));
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        UserAccount user = users.findByEmailIgnoreCase(request.email()).orElseThrow();
        return AuthResponse.from(user, tokens.issue(user));
    }

    public record RegisterRequest(@NotBlank String name, @Email @NotBlank String email,
                                  @Size(min = 8, max = 72) String password) {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record AuthResponse(String token, Long id, String name, String email, UserRole role) {
        static AuthResponse from(UserAccount user, String token) {
            return new AuthResponse(token, user.getId(), user.getName(), user.getEmail(), user.getRole());
        }
    }
}
