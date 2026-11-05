package com.cinebook.sheets;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Production implementation of TicketSheetClient communicating with Google Sheets API v4.
 * Provides idempotent row upserting using service account credentials.
 */
@Component
@ConditionalOnProperty(name = "google.sheets.enabled", havingValue = "true")
public class GoogleSheetsTicketClient implements TicketSheetClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsTicketClient.class);
    private static final String VALUE_INPUT_OPTION = "RAW";

    private final String spreadsheetId;
    private final String tabName;
    private final Sheets sheetsService;
    private final String serviceAccountEmail;
    private final boolean ready;

    /**
     * Testing constructor allowing injection of a mocked Sheets service.
     */
    public GoogleSheetsTicketClient(Sheets sheetsService, String spreadsheetId, String tabName) {
        this.sheetsService = sheetsService;
        this.spreadsheetId = spreadsheetId != null ? spreadsheetId.trim() : "";
        this.tabName = (tabName != null && !tabName.isBlank()) ? tabName.trim() : "Tickets";
        this.serviceAccountEmail = "test-service-account@test.iam.gserviceaccount.com";
        this.ready = this.sheetsService != null && !this.spreadsheetId.isEmpty();
    }

    /**
     * Spring-managed constructor loading credentials from file or raw JSON content.
     */
    @Autowired
    public GoogleSheetsTicketClient(
            @Value("${google.sheets.sheet-id:}") String spreadsheetId,
            @Value("${google.sheets.tab:Tickets}") String tabName,
            @Value("${google.sheets.credentials-file:}") String credentialsFile,
            @Value("${google.sheets.credentials-json:}") String credentialsJson,
            @Autowired(required = false) Sheets customSheetsService
    ) {
        this.spreadsheetId = spreadsheetId != null ? spreadsheetId.trim() : "";
        this.tabName = (tabName != null && !tabName.isBlank()) ? tabName.trim() : "Tickets";

        if (customSheetsService != null) {
            this.sheetsService = customSheetsService;
            this.serviceAccountEmail = "injected-service-account";
            this.ready = true;
            return;
        }

        Sheets initializedService = null;
        String clientEmail = null;
        boolean isClientReady = false;

        try {
            GoogleCredentials credentials = null;
            if (credentialsJson != null && !credentialsJson.trim().isEmpty()) {
                credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8)))
                        .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS));
            } else if (credentialsFile != null && !credentialsFile.trim().isEmpty()) {
                File file = new File(credentialsFile.trim());
                if (file.exists()) {
                    credentials = GoogleCredentials.fromStream(new FileInputStream(file))
                            .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS));
                } else {
                    log.warn("Google Sheets credentials file does not exist at path: '{}'", credentialsFile);
                }
            }

            if (credentials != null) {
                if (credentials instanceof ServiceAccountCredentials sac) {
                    clientEmail = sac.getClientEmail();
                }
                HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);
                initializedService = new Sheets.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        requestInitializer
                ).setApplicationName("CineBook").build();
                isClientReady = !this.spreadsheetId.isEmpty();
            } else {
                log.warn("No Google credentials provided (neither file nor JSON content found).");
            }
        } catch (Exception ex) {
            log.warn("Failed to initialize Google Sheets API client: {}", ex.getMessage());
        }

        this.sheetsService = initializedService;
        this.serviceAccountEmail = clientEmail != null ? clientEmail : "unknown";
        this.ready = isClientReady;
    }

    @Override
    public boolean isReady() {
        return ready && sheetsService != null && !spreadsheetId.isEmpty();
    }

    public String getServiceAccountEmail() {
        return serviceAccountEmail;
    }

    public String getSpreadsheetId() {
        return spreadsheetId;
    }

    public String getTabName() {
        return tabName;
    }

    public Sheets getSheetsService() {
        return sheetsService;
    }

    @Override
    public void upsertBooking(TicketRow row) {
        if (!isReady()) {
            throw new IllegalStateException("Google Sheets client is not configured or ready. Verify GOOGLE_SHEET_ID and credentials.");
        }
        if (row == null || row.bookingId() == null || row.bookingId().isBlank()) {
            throw new IllegalArgumentException("Cannot sync null or empty ticket row.");
        }

        try {
            // 1. Read column A to look for matching Booking ID
            String columnARange = tabName + "!A:A";
            ValueRange colAData = executeWithRetry(() -> sheetsService.spreadsheets().values()
                    .get(spreadsheetId, columnARange)
                    .execute());

            List<List<Object>> rows = colAData != null ? colAData.getValues() : null;

            // If tab is completely empty, write the header row first
            if (rows == null || rows.isEmpty()) {
                ensureHeaderRow();
                // Append row directly
                appendRow(row);
                return;
            }

            // 2. Search for existing Booking ID (case-insensitive)
            int matchedRowIndex = -1;
            for (int i = 0; i < rows.size(); i++) {
                List<Object> cell = rows.get(i);
                if (cell != null && !cell.isEmpty()) {
                    String existingId = String.valueOf(cell.get(0)).trim();
                    if (row.bookingId().trim().equalsIgnoreCase(existingId)) {
                        matchedRowIndex = i + 1; // Google Sheets is 1-indexed
                        break;
                    }
                }
            }

            // 3. Update if found; otherwise append
            if (matchedRowIndex > 0) {
                updateRow(matchedRowIndex, row);
            } else {
                appendRow(row);
            }
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception ex) {
            throw mapException(ex);
        }
    }

    private void ensureHeaderRow() throws IOException {
        String headerRange = tabName + "!A1:M1";
        ValueRange headerBody = new ValueRange()
                .setValues(List.of(TicketRow.HEADERS.stream().map(h -> (Object) h).toList()));
        executeWithRetry(() -> sheetsService.spreadsheets().values()
                .update(spreadsheetId, headerRange, headerBody)
                .setValueInputOption(VALUE_INPUT_OPTION)
                .execute());
    }

    private void updateRow(int rowIndex, TicketRow row) throws IOException {
        String range = tabName + "!A" + rowIndex + ":M" + rowIndex;
        ValueRange body = new ValueRange().setValues(List.of(row.toRowValues()));
        executeWithRetry(() -> sheetsService.spreadsheets().values()
                .update(spreadsheetId, range, body)
                .setValueInputOption(VALUE_INPUT_OPTION)
                .execute());
        log.info("Updated existing Google Sheet row {} for booking {}", rowIndex, row.bookingId());
    }

    private void appendRow(TicketRow row) throws IOException {
        String range = tabName + "!A:M";
        ValueRange body = new ValueRange().setValues(List.of(row.toRowValues()));
        executeWithRetry(() -> sheetsService.spreadsheets().values()
                .append(spreadsheetId, range, body)
                .setValueInputOption(VALUE_INPUT_OPTION)
                .setInsertDataOption("INSERT_ROWS")
                .execute());
        log.info("Appended new Google Sheet row for booking {}", row.bookingId());
    }

    /**
     * Executes Google API calls with exponential backoff on HTTP 429 and 5xx errors.
     */
    @FunctionalInterface
    public interface GoogleApiCallable<T> {
        T call() throws IOException;
    }

    private <T> T executeWithRetry(GoogleApiCallable<T> action) throws IOException {
        int maxAttempts = 3;
        long delayMs = 200;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.call();
            } catch (GoogleJsonResponseException gje) {
                int status = gje.getStatusCode();
                if (status == 403) {
                    throw new RuntimeException("Share the sheet with the service account email as Editor", gje);
                }
                if (status == 404) {
                    throw new RuntimeException("Sheet ID or tab name not found", gje);
                }
                if ((status == 429 || (status >= 500 && status < 600)) && attempt < maxAttempts) {
                    log.warn("Google API returned HTTP {}. Retrying attempt {}/{} in {} ms...", status, attempt, maxAttempts, delayMs);
                    sleep(delayMs);
                    delayMs *= 2;
                    continue;
                }
                throw gje;
            } catch (IOException ioe) {
                if (attempt < maxAttempts) {
                    log.warn("IO error during Sheets call (attempt {}/{}). Retrying in {} ms: {}", attempt, maxAttempts, delayMs, ioe.getMessage());
                    sleep(delayMs);
                    delayMs *= 2;
                    continue;
                }
                throw ioe;
            }
        }
        throw new IOException("Failed Google Sheets API operation after " + maxAttempts + " attempts.");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private RuntimeException mapException(Exception ex) {
        if (ex instanceof RuntimeException re) {
            return re;
        }
        if (ex instanceof GoogleJsonResponseException gje) {
            if (gje.getStatusCode() == 403) {
                return new RuntimeException("Share the sheet with the service account email as Editor", gje);
            }
            if (gje.getStatusCode() == 404) {
                return new RuntimeException("Sheet ID or tab name not found", gje);
            }
        }
        return new RuntimeException("Google Sheets API error: " + ex.getMessage(), ex);
    }
}
