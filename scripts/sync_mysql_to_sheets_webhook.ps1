# CineBook - Sync Local MySQL Database to Google Sheets via Apps Script Webhook
param (
    [string]$WebhookUrl = ""
)

if (-not $WebhookUrl) {
    Write-Host "Usage: .\sync_mysql_to_sheets_webhook.ps1 -WebhookUrl 'https://script.google.com/macros/s/.../exec'" -ForegroundColor Yellow
    exit 1
}

$mysql = "C:\Users\Admin\mysql-server\bin\mysql.exe"
$user = "root"
$pass = "cinebook123"

$sql = @"
USE cinebook;
SELECT 
    CONCAT('BK', b.id) AS Booking_ID,
    s.movie_name AS Movie_Title,
    TIME_FORMAT(s.show_time, '%h:%i %p') AS Showtime,
    GROUP_CONCAT(CONCAT(st.row_label, st.seat_no) ORDER BY st.row_label, st.seat_no SEPARATOR ', ') AS Reserved_Seats,
    b.subtotal AS Subtotal_Rs,
    IFNULL(b.coupon_code, 'NONE') AS Coupon,
    b.discount_amount AS Discount_Rs,
    b.total_amount AS Total_Paid_Rs,
    IFNULL(b.refund_amount, 0.00) AS Refund_Issued_Rs,
    b.status AS Booking_Status,
    DATE_FORMAT(b.created_at, '%Y-%m-%d %H:%i') AS Booked_At,
    IFNULL(DATE_FORMAT(b.cancelled_at, '%Y-%m-%d %H:%i'), '-') AS Cancelled_At
FROM bookings b
JOIN shows s ON b.show_id = s.id
JOIN booking_seats bs ON b.id = bs.booking_id
JOIN seats st ON bs.seat_id = st.id
GROUP BY b.id, s.movie_name, s.show_time, b.subtotal, b.coupon_code, b.discount_amount, b.total_amount, b.refund_amount, b.status, b.created_at, b.cancelled_at
ORDER BY b.id ASC;
"@

Write-Host "Querying local MySQL database..." -ForegroundColor Cyan
$output = & $mysql -u $user --password=$pass -B -e $sql

$lines = $output -split "`r?`n" | Where-Object { $_.Trim() -ne "" }
$rows = @()

# Skip header (line 0)
for ($i = 1; $i -lt $lines.Count; $i++) {
    $cols = $lines[$i] -split "`t"
    $rows += ,@($cols)
}

$payload = @{
    action = "BULK_SYNC"
    rows = $rows
} | ConvertTo-Json -Depth 5

Write-Host "Sending $($rows.Count) booking records to Google Sheets..." -ForegroundColor Cyan
$response = Invoke-RestMethod -Uri $WebhookUrl -Method Post -Body $payload -ContentType "application/json"
Write-Host "Response from Google Sheets: $($response | ConvertTo-Json -Compress)" -ForegroundColor Green

