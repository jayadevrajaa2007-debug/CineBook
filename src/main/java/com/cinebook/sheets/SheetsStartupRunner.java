package com.cinebook.sheets;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Startup self-check verifying connectivity to Google Sheets when enabled.
 * Creates the target tab and header row if missing, and logs a clear one-line status.
 * Any network, credential, or permission failures only log a warning and never prevent startup.
 */
@Component
public class SheetsStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SheetsStartupRunner.class);

    private final boolean sheetsEnabled;
    private final TicketSheetClient ticketSheetClient;

    public SheetsStartupRunner(
            @Value("${google.sheets.enabled:false}") boolean sheetsEnabled,
            TicketSheetClient ticketSheetClient
    ) {
        this.sheetsEnabled = sheetsEnabled;
        this.ticketSheetClient = ticketSheetClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!sheetsEnabled) {
            log.info("Google Sheets sync is disabled");
            return;
        }

        if (!(ticketSheetClient instanceof GoogleSheetsTicketClient gClient)) {
            log.info("Google Sheets sync is disabled (No-Op client active)");
            return;
        }

        if (!gClient.isReady()) {
            log.warn("Google Sheets startup check: Client is not ready. Verify GOOGLE_SHEET_ID and service account credentials file.");
            return;
        }

        try {
            Sheets sheets = gClient.getSheetsService();
            String spreadsheetId = gClient.getSpreadsheetId();
            String tabName = gClient.getTabName();

            // 1. Verify access to spreadsheet and find if tab exists
            Spreadsheet spreadsheet = sheets.spreadsheets().get(spreadsheetId).execute();
            boolean tabExists = spreadsheet.getSheets() != null && spreadsheet.getSheets().stream()
                    .anyMatch(s -> s.getProperties() != null && tabName.equalsIgnoreCase(s.getProperties().getTitle()));

            if (!tabExists) {
                AddSheetRequest addSheetRequest = new AddSheetRequest()
                        .setProperties(new SheetProperties().setTitle(tabName));
                BatchUpdateSpreadsheetRequest batchUpdate = new BatchUpdateSpreadsheetRequest()
                        .setRequests(List.of(new Request().setAddSheet(addSheetRequest)));
                sheets.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute();
                log.info("Created missing sheet tab '{}' in spreadsheet {}", tabName, spreadsheetId);
            }

            // 2. Check if row 1 is empty, if so populate the standard headers
            ValueRange cellA1 = sheets.spreadsheets().values()
                    .get(spreadsheetId, tabName + "!A1")
                    .execute();

            if (cellA1.getValues() == null || cellA1.getValues().isEmpty() || cellA1.getValues().get(0).isEmpty()) {
                ValueRange headerRange = new ValueRange()
                        .setValues(List.of(TicketRow.HEADERS.stream().map(h -> (Object) h).toList()));
                sheets.spreadsheets().values()
                        .update(spreadsheetId, tabName + "!A1:M1", headerRange)
                        .setValueInputOption("RAW")
                        .execute();
                log.info("Wrote initial header row to sheet tab '{}'", tabName);
            }

            log.info("Google Sheets sync initialized successfully for service account '{}' on sheet ID '{}' [tab: '{}']",
                    gClient.getServiceAccountEmail(), spreadsheetId, tabName);

        } catch (GoogleJsonResponseException gje) {
            int code = gje.getStatusCode();
            String reason = switch (code) {
                case 403 -> "Share the sheet with the service account email as Editor";
                case 404 -> "Sheet ID or tab name not found";
                default -> "HTTP " + code + " - " + gje.getMessage();
            };
            log.warn("Google Sheets startup check failed for service account '{}': {}. Sync will retry in background.",
                    gClient.getServiceAccountEmail(), reason);
        } catch (Exception ex) {
            log.warn("Google Sheets startup check failed: {}. Sync will retry in background.", ex.getMessage());
        }
    }
}
