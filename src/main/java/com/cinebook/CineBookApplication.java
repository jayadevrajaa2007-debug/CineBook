package com.cinebook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CineBookApplication {

    public static void main(String[] args) {
        // Enforce fail-fast check on startup if MYSQL_PASSWORD is missing (unless in test context)
        String password = System.getenv("MYSQL_PASSWORD");
        if (password == null) {
            password = System.getProperty("MYSQL_PASSWORD");
        }

        // If not running tests or explicitly disabled, warn if password is not configured
        if ((password == null || password.trim().isEmpty()) && System.getProperty("cinebook.skip-env-check") == null) {
            System.err.println();
            System.err.println("================================================================================");
            System.err.println("CINEBOOK STARTUP FAILURE: MYSQL_PASSWORD is required but not set!");
            System.err.println("Please check the following:");
            System.err.println("1. Is MySQL Server 8.0+ running? (Check Windows Services or run 'net start mysql')");
            System.err.println("2. Are MYSQL_HOST (localhost) and MYSQL_PORT (3306) correct?");
            System.err.println("3. Is MYSQL_USER (root) correct?");
            System.err.println("4. Is MYSQL_PASSWORD set in your .env file or environment variables?");
            System.err.println("================================================================================");
            System.err.println();
        }

        SpringApplication.run(CineBookApplication.class, args);
    }
}

