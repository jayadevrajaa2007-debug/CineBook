package com.cinebook.sheets;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleSheetsTicketClientTest {

    private static final String SPREADSHEET_ID = "test-spreadsheet-id";
    private static final String TAB_NAME = "Tickets";

    @Mock
    private Sheets sheetsService;

    @Mock
    private Sheets.Spreadsheets spreadsheets;

    @Mock
    private Sheets.Spreadsheets.Values values;

    @Mock
    private Sheets.Spreadsheets.Values.Get getRequest;

    @Mock
    private Sheets.Spreadsheets.Values.Update updateRequest;

    @Mock
    private Sheets.Spreadsheets.Values.Append appendRequest;

    private GoogleSheetsTicketClient client;
    private TicketRow ticketRow;

    @BeforeEach
    void setUp() throws IOException {
        when(sheetsService.spreadsheets()).thenReturn(spreadsheets);
        when(spreadsheets.values()).thenReturn(values);

        client = new GoogleSheetsTicketClient(sheetsService, SPREADSHEET_ID, TAB_NAME);

        ticketRow = new TicketRow(
                "BK1001",
                "Interstellar",
                "7:30 PM",
                "C3, C4",
                "Premium, Premium",
                new BigDecimal("500.00"),
                "CINE10",
                new BigDecimal("50.00"),
                new BigDecimal("450.00"),
                "CONFIRMED",
                null,
                "2026-11-06 19:30:12",
                ""
        );
    }

    @Test
    @DisplayName("Upsert updates existing row when Booking ID is already present in column A")
    void testUpsertUpdatesExistingRow() throws IOException {
        // Mock column A containing: row 1: Header, row 2: BK1000, row 3: BK1001
        ValueRange colA = new ValueRange().setValues(List.of(
                List.of("Booking ID"),
                List.of("BK1000"),
                List.of("BK1001") // Row index 3 (1-based)
        ));

        when(values.get(SPREADSHEET_ID, "Tickets!A:A")).thenReturn(getRequest);
        when(getRequest.execute()).thenReturn(colA);

        when(values.update(eq(SPREADSHEET_ID), eq("Tickets!A3:M3"), any(ValueRange.class)))
                .thenReturn(updateRequest);
        when(updateRequest.setValueInputOption("RAW")).thenReturn(updateRequest);
        when(updateRequest.execute()).thenReturn(new UpdateValuesResponse());

        client.upsertBooking(ticketRow);

        // Verify that row 3 was updated with the new values
        ArgumentCaptor<ValueRange> bodyCaptor = ArgumentCaptor.forClass(ValueRange.class);
        verify(values, times(1)).update(eq(SPREADSHEET_ID), eq("Tickets!A3:M3"), bodyCaptor.capture());
        verify(values, never()).append(any(), any(), any());

        List<List<Object>> capturedValues = bodyCaptor.getValue().getValues();
        assertEquals(1, capturedValues.size());
        assertEquals("BK1001", capturedValues.get(0).get(0));
        assertEquals("Interstellar", capturedValues.get(0).get(1));
    }

    @Test
    @DisplayName("Upsert appends a new row when Booking ID is not found in column A")
    void testUpsertAppendsNewRowWhenNotFound() throws IOException {
        // Mock column A with Header and BK1000 only
        ValueRange colA = new ValueRange().setValues(List.of(
                List.of("Booking ID"),
                List.of("BK1000")
        ));

        when(values.get(SPREADSHEET_ID, "Tickets!A:A")).thenReturn(getRequest);
        when(getRequest.execute()).thenReturn(colA);

        when(values.append(eq(SPREADSHEET_ID), eq("Tickets!A:M"), any(ValueRange.class)))
                .thenReturn(appendRequest);
        when(appendRequest.setValueInputOption("RAW")).thenReturn(appendRequest);
        when(appendRequest.setInsertDataOption("INSERT_ROWS")).thenReturn(appendRequest);
        when(appendRequest.execute()).thenReturn(new AppendValuesResponse());

        client.upsertBooking(ticketRow);

        // Verify append was called
        verify(values, times(1)).append(eq(SPREADSHEET_ID), eq("Tickets!A:M"), any(ValueRange.class));
        verify(values, never()).update(eq(SPREADSHEET_ID), contains(":M"), any());
    }

    @Test
    @DisplayName("Upsert writes header row first if the sheet is completely empty, then appends row")
    void testUpsertWritesHeaderWhenSheetIsEmpty() throws IOException {
        // Mock empty sheet
        ValueRange emptyColA = new ValueRange().setValues(null);

        when(values.get(SPREADSHEET_ID, "Tickets!A:A")).thenReturn(getRequest);
        when(getRequest.execute()).thenReturn(emptyColA);

        // Header update mock
        when(values.update(eq(SPREADSHEET_ID), eq("Tickets!A1:M1"), any(ValueRange.class)))
                .thenReturn(updateRequest);
        when(updateRequest.setValueInputOption("RAW")).thenReturn(updateRequest);
        when(updateRequest.execute()).thenReturn(new UpdateValuesResponse());

        // Append row mock
        when(values.append(eq(SPREADSHEET_ID), eq("Tickets!A:M"), any(ValueRange.class)))
                .thenReturn(appendRequest);
        when(appendRequest.setValueInputOption("RAW")).thenReturn(appendRequest);
        when(appendRequest.setInsertDataOption("INSERT_ROWS")).thenReturn(appendRequest);
        when(appendRequest.execute()).thenReturn(new AppendValuesResponse());

        client.upsertBooking(ticketRow);

        // Verify header was created at A1:M1 and row was appended
        verify(values, times(1)).update(eq(SPREADSHEET_ID), eq("Tickets!A1:M1"), any(ValueRange.class));
        verify(values, times(1)).append(eq(SPREADSHEET_ID), eq("Tickets!A:M"), any(ValueRange.class));
    }
}
