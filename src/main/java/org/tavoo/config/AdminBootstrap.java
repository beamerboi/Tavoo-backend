package org.tavoo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.tavoo.entity.Role;
import org.tavoo.entity.User;
import org.tavoo.repository.UserRepository;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${tavoo.bootstrap-admin.username:}") String username,
            @Value("${tavoo.bootstrap-admin.password:}") String password
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) {
            return;
        }
        if (username.isBlank() || password.length() < 8) {
            throw new IllegalStateException(
                    "Bootstrap admin requires a username and a password of at least 8 characters"
            );
        }
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (userRepository.existsByUsernameIgnoreCase(username.trim())) {
            throw new IllegalStateException(
                    "Bootstrap administrator username is already used by another account"
            );
        }
        userRepository.save(new User(
                username.trim(),
                passwordEncoder.encode(password),
                Role.ADMIN
        ));
        LOGGER.info("Created bootstrap administrator '{}'", username.trim());
    }
}
