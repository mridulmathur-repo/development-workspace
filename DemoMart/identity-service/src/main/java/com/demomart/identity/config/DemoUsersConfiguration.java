package com.demomart.identity.config;

import com.demomart.identity.user.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DemoUsersConfiguration {
    @Bean
    CommandLineRunner seedUsers(UserAccountRepository users, PasswordEncoder encoder,
                                @Value("${app.seed-demo-users:true}") boolean enabled) {
        return args -> {
            if (!enabled) return;
            create(users, encoder, "Demo Customer", "user@demomart.local", "User@123", UserRole.END_USER);
            create(users, encoder, "Inventory Manager", "inventory@demomart.local", "Inventory@123", UserRole.INVENTORY_MANAGER);
            create(users, encoder, "Demo Administrator", "admin@demomart.local", "Admin@123", UserRole.ADMIN);
        };
    }

    private void create(UserAccountRepository users, PasswordEncoder encoder, String name,
                        String email, String password, UserRole role) {
        if (!users.existsByEmailIgnoreCase(email))
            users.save(new UserAccount(name, email, encoder.encode(password), role));
    }
}

