/**
 * CineBook — Google Sheets Live Database Engine
 * Paste this script into your Google Apps Script project:
 * https://script.google.com/home/projects/1TM40y1SBPJlCtL0mWkPfkZ-MD3OvdHcXislq_YNg011DyuG2HZib55qW/edit
 *
 * Connected Spreadsheet:
 * https://docs.google.com/spreadsheets/d/1TNZNxdqQ37BX-w8Gj7wmrXLCV2AQ5vzQeYPXmh3R4jI/edit
 */

const SPREADSHEET_ID = '1TNZNxdqQ37BX-w8Gj7wmrXLCV2AQ5vzQeYPXmh3R4jI';

/**
 * Run this function once to set up all 4 database tables with styled headers,
 * frozen top rows, and initial show/coupon configuration.
 */
function setupDatabase() {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);

  // 1. Tickets Table (13 Columns A to M)
  let ticketsSheet = ss.getSheetByName('Tickets');
  if (!ticketsSheet) {
    ticketsSheet = ss.insertSheet('Tickets');
  }
  ticketsSheet.clear();
  const ticketHeaders = [
    ['Booking ID', 'Movie', 'Show Time', 'Seats', 'Seat Types', 'Subtotal', 'Coupon', 'Discount', 'Total', 'Status', 'Refund', 'Booked At', 'Cancelled At']
  ];
  ticketsSheet.getRange(1, 1, 1, ticketHeaders[0].length)
    .setValues(ticketHeaders)
    .setBackground('#8c1c2b')
    .setFontColor('#ffffff')
    .setFontWeight('bold');
  ticketsSheet.setFrozenRows(1);

  // 2. Shows Table
  let showsSheet = ss.getSheetByName('Shows');
  if (!showsSheet) {
    showsSheet = ss.insertSheet('Shows');
  }
  showsSheet.clear();
  const showHeaders = [
    ['Show ID', 'Movie Title', 'Showtime', 'Total Seats', 'Available Seats', 'Booked Seats']
  ];
  showsSheet.getRange(1, 1, 1, showHeaders[0].length)
    .setValues(showHeaders)
    .setBackground('#2f6b4f')
    .setFontColor('#ffffff')
    .setFontWeight('bold');
  showsSheet.setFrozenRows(1);

  // Pre-seed 3 shows
  const initialShows = [
    [1, 'Interstellar', '07:30 PM', 40, 36, 4],
    [2, 'Inception', '04:00 PM', 40, 38, 2],
    [3, 'The Dark Knight', '10:00 PM', 40, 40, 0]
  ];
  showsSheet.getRange(2, 1, initialShows.length, initialShows[0].length).setValues(initialShows);

  // 3. Coupons Table
  let couponsSheet = ss.getSheetByName('Coupons');
  if (!couponsSheet) {
    couponsSheet = ss.insertSheet('Coupons');
  }
  couponsSheet.clear();
  const couponHeaders = [['Coupon Code', 'Discount Percent', 'Active']];
  couponsSheet.getRange(1, 1, 1, couponHeaders[0].length)
    .setValues(couponHeaders)
    .setBackground('#b8872b')
    .setFontColor('#ffffff')
    .setFontWeight('bold');
  couponsSheet.setFrozenRows(1);
  couponsSheet.getRange(2, 1, 1, 3).setValues([['CINE10', '10%', 'YES']]);

  // Remove default "Sheet1" if empty
  const defaultSheet = ss.getSheetByName('Sheet1');
  if (defaultSheet && ss.getSheets().length > 1) {
    try { ss.deleteSheet(defaultSheet); } catch (e) {}
  }

  Logger.log('CineBook database tables initialized successfully!');
}

/**
 * Webhook Endpoint: Receives live bookings and cancellations from CineBook
 * Supports: POST requests with JSON payload
 */
function doPost(e) {
  try {
    const data = JSON.parse(e.postData.contents);
    const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
    const action = data.action || 'CREATE_BOOKING';

    if (action === 'CREATE_BOOKING') {
      const bookingsSheet = ss.getSheetByName('Bookings');
      const newRow = [
        data.bookingId,        // e.g. "BK1012"
        data.movieTitle,       // e.g. "Interstellar"
        data.showtime,         // e.g. "07:30 PM"
        data.reservedSeats,    // e.g. "C3, C4"
        Number(data.subtotal), // 500.00
        data.coupon || 'NONE', // "CINE10"
        Number(data.discount), // 50.00
        Number(data.total),    // 450.00
        0.00,                  // Refund
        'CONFIRMED',           // Status
        data.bookedAt || new Date().toLocaleString(),
        '-'
      ];
      bookingsSheet.appendRow(newRow);

      // Highlight confirmed row
      const lastRow = bookingsSheet.getLastRow();
      bookingsSheet.getRange(lastRow, 10).setBackground('#d4edda').setFontColor('#155724');

      return ContentService.createTextOutput(JSON.stringify({ status: 'SUCCESS', message: 'Booking stored in Google Sheets', bookingId: data.bookingId }))
        .setMimeType(ContentService.MimeType.JSON);
    }

    if (action === 'CANCEL_BOOKING') {
      const bookingsSheet = ss.getSheetByName('Bookings');
      const values = bookingsSheet.getDataRange().getValues();
      const targetId = String(data.bookingId).trim().toUpperCase();

      for (let r = 1; r < values.length; r++) {
        if (String(values[r][0]).trim().toUpperCase() === targetId) {
          // Update Status to CANCELLED
          bookingsSheet.getRange(r + 1, 9).setValue(Number(data.refundAmount)); // Refund
          bookingsSheet.getRange(r + 1, 10).setValue('CANCELLED').setBackground('#f8d7da').setFontColor('#721c24');
          bookingsSheet.getRange(r + 1, 12).setValue(data.cancelledAt || new Date().toLocaleString());

          return ContentService.createTextOutput(JSON.stringify({ status: 'SUCCESS', message: 'Booking cancelled in Google Sheets', bookingId: targetId }))
            .setMimeType(ContentService.MimeType.JSON);
        }
      }

      return ContentService.createTextOutput(JSON.stringify({ status: 'ERROR', message: 'Booking ID not found' }))
        .setMimeType(ContentService.MimeType.JSON);
    }

    if (action === 'BULK_SYNC') {
      const bookingsSheet = ss.getSheetByName('Bookings');
      // If bulk sync payload contains array of rows
      if (Array.isArray(data.rows) && data.rows.length > 0) {
        bookingsSheet.getRange(2, 1, bookingsSheet.getLastRow() > 1 ? bookingsSheet.getLastRow() - 1 : 1, 12).clearContent();
        bookingsSheet.getRange(2, 1, data.rows.length, data.rows[0].length).setValues(data.rows);
      }
      return ContentService.createTextOutput(JSON.stringify({ status: 'SUCCESS', message: 'Bulk database sync complete', count: data.rows.length }))
        .setMimeType(ContentService.MimeType.JSON);
    }

    return ContentService.createTextOutput(JSON.stringify({ status: 'ERROR', message: 'Unknown action' }))
      .setMimeType(ContentService.MimeType.JSON);

  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: 'ERROR', message: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

/**
 * Webhook GET: Returns all bookings as JSON (enables Google Sheets to act as a REST API)
 */
function doGet(e) {
  try {
    const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
    const bookingsSheet = ss.getSheetByName('Bookings');
    const values = bookingsSheet.getDataRange().getValues();
    const headers = values[0];
    const bookings = [];

    for (let r = 1; r < values.length; r++) {
      const row = values[r];
      const entry = {};
      for (let c = 0; c < headers.length; c++) {
        entry[headers[c]] = row[c];
      }
      bookings.push(entry);
    }

    return ContentService.createTextOutput(JSON.stringify({
      status: 'OK',
      totalBookings: bookings.length,
      bookings: bookings
    })).setMimeType(ContentService.MimeType.JSON);

  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: 'ERROR', message: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

