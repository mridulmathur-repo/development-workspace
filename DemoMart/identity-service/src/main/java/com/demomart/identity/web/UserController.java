package com.demomart.identity.web;

import com.demomart.identity.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserAccountRepository users;
    public UserController(UserAccountRepository users) { this.users = users; }

    @GetMapping("/me")
    UserView me(@AuthenticationPrincipal Jwt jwt) { return UserView.from(current(jwt)); }

    @PutMapping("/me/address")
    UserView updateAddress(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest request) {
        UserAccount user = current(jwt);
        user.updateAddress(request.addressLine1(), request.addressLine2(), request.city(), request.postalCode(), request.country());
        return UserView.from(users.save(user));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<UserView> allUsers() { return users.findAll().stream().map(UserView::from).toList(); }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    UserView changeRole(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        UserAccount user = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setRole(request.role());
        return UserView.from(users.save(user));
    }

    private UserAccount current(Jwt jwt) {
        return users.findById(Long.valueOf(jwt.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public record AddressRequest(@NotBlank String addressLine1, String addressLine2,
                                 @NotBlank String city, @NotBlank String postalCode,
                                 @NotBlank String country) {}
    public record RoleRequest(@NotNull UserRole role) {}
    public record UserView(Long id, String name, String email, UserRole role,
                           String addressLine1, String addressLine2, String city,
                           String postalCode, String country) {
        static UserView from(UserAccount user) {
            return new UserView(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                    user.getAddressLine1(), user.getAddressLine2(), user.getCity(),
                    user.getPostalCode(), user.getCountry());
        }
    }
}
