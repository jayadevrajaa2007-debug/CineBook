package com.cinebook.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Custom FailureAnalyzer that catches database connectivity issues on startup
 * and produces a clear, actionable error banner instead of a raw stack trace.
 */
public class DatabaseFailureAnalyzer extends AbstractFailureAnalyzer<Throwable> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, Throwable cause) {
        String causeMessage = cause != null ? cause.getMessage() : (rootFailure != null ? rootFailure.getMessage() : "");
        String failureText = rootFailure != null ? rootFailure.toString() : "";

        boolean isDbIssue = failureText.contains("CommunicationsException") ||
                failureText.contains("ConnectException") ||
                failureText.contains("HikariPool") ||
                failureText.contains("Access denied for user") ||
                failureText.contains("Unknown database") ||
                causeMessage.contains("Communications link failure") ||
                causeMessage.contains("Connection refused");

        if (isDbIssue) {
            String description = "CineBook failed to connect to MySQL database.\n" +
                    "Root Cause: " + causeMessage;

            String action = "\n================================================================================\n" +
                    "DATABASE CONNECTION CHECKLIST:\n" +
                    "1. Is MySQL Server 8.0+ running? (Check Windows Services or run 'net start mysql')\n" +
                    "2. Are MYSQL_HOST (localhost) and MYSQL_PORT (3306) correct and reachable?\n" +
                    "3. Is MYSQL_USER (root) correct?\n" +
                    "4. Is MYSQL_PASSWORD set correctly in your .env file or environment variables?\n" +
                    "5. Does the 'cinebook' database exist or user has permissions to create it?\n" +
                    "================================================================================";

            return new FailureAnalysis(description, action, cause != null ? cause : rootFailure);
        }

        return null;
    }
}

