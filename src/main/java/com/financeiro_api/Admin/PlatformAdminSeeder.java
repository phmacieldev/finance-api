package com.financeiro_api.Admin;

import com.financeiro_api.Users.domain.Role;
import com.financeiro_api.Users.domain.User;
import com.financeiro_api.Users.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlatformAdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminSeeder.class);

    @Value("${app.platform.admin.email:admin@plataforma.com}")
    private String adminEmail;

    @Value("${app.platform.admin.password:admin@1234}")
    private String adminPassword;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PlatformAdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    @Transactional
    public void seed() {
        if (userRepository.existsByRole(Role.PLATFORM_ADMIN)) return;

        userRepository.save(
                User.builder()
                        .name("Administrador da Plataforma")
                        .email(adminEmail)
                        .password(passwordEncoder.encode(adminPassword))
                        .role(Role.PLATFORM_ADMIN)
                        .emailVerificado(true)
                        .build()
        );

        log.info("Platform admin created: {}", adminEmail);
    }
}
