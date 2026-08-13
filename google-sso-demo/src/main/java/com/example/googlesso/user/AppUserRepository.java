package com.example.googlesso.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByGoogleSubject(String googleSubject);
    long countByRole(UserRole role);
}
