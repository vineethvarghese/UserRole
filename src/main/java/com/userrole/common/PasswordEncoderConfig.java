package com.userrole.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Standalone configuration for the PasswordEncoder bean.
 *
 * Extracted from SecurityConfig to break a three-way circular dependency:
 *   SecurityConfig → LocalAuthProvider → UserServiceImpl → PasswordEncoder (SecurityConfig)
 *
 * Moving the PasswordEncoder bean here eliminates the cycle because this class
 * has no dependencies on any application beans.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
