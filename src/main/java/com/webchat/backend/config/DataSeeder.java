package com.webchat.backend.config;

import com.webchat.backend.entity.User;
import com.webchat.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN temperature FLOAT DEFAULT 0.7");
        } catch (Exception e) {
            log.warn("temperature column already exists or could not be added");
        }
        try {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN max_tokens INTEGER DEFAULT 2048");
        } catch (Exception e) {
            log.warn("max_tokens column already exists or could not be added");
        }

        String testEmail = "admin@gmail.com";
        if (userRepository.findByEmail(testEmail).isEmpty()) {
            log.info("Test environment detected. Seeding default user: {}", testEmail);
            User user = User.builder()
                    .name("Test User")
                    .email(testEmail)
                    .passwordHash(passwordEncoder.encode("123456"))
                    .build();
            userRepository.save(user);
            log.info("Default test user created. Email: {} | Password: 123456", testEmail);
        }
    }
}
