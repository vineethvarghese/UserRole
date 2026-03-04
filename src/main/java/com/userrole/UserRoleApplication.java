package com.userrole;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class UserRoleApplication {

    public static void main(String[] args) {
        // Force JVM timezone to UTC so all Instant values are stored and logged consistently
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(UserRoleApplication.class, args);
    }
}
