package com.cinebook.sheets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.Executor;

/**
 * Configuration for Google Sheets integration, asynchronous execution,
 * scheduled background sync, and regional timezone Clock bean.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class GoogleSheetsConfig {

    public static final String ZONE_KOLKATA = "Asia/Kolkata";

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(ZONE_KOLKATA));
    }

    @Bean(name = "sheetSyncExecutor")
    public Executor sheetSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("sheet-sync-");
        executor.initialize();
        return executor;
    }
}
