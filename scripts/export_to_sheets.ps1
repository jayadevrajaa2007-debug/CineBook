# Export CineBook MySQL Database to CSV files formatted for Google Sheets
$exportDir = "c:\Users\Admin\OneDrive\Desktop\judei\sheets_export"
if (!(Test-Path $exportDir)) {
    New-Item -ItemType Directory -Path $exportDir -Force | Out-Null
}

$mysql = "C:\Users\Admin\mysql-server\bin\mysql.exe"
$user = "root"
$pass = "cinebook123"
$db = "cinebook"

# 1. Bookings Report
$sqlBookings = @"
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
ORDER BY b.id DESC;
"@

& $mysql -u $user --password=$pass -B -e $sqlBookings | ForEach-Object { $_ -replace "\t", "," } | Out-File -FilePath "$exportDir\01_Bookings_Report.csv" -Encoding UTF8

# 2. Shows Schedule
$sqlShows = @"
USE cinebook;
SELECT 
    s.id AS Show_ID,
    s.movie_name AS Movie_Title,
    TIME_FORMAT(s.show_time, '%h:%i %p') AS Showtime,
    COUNT(st.id) AS Total_Capacity,
    (COUNT(st.id) - COUNT(bs.seat_id)) AS Available_Seats,
    COUNT(bs.seat_id) AS Booked_Seats
FROM shows s
JOIN seats st ON s.id = st.show_id
LEFT JOIN booking_seats bs ON st.id = bs.seat_id AND bs.active = 1
GROUP BY s.id, s.movie_name, s.show_time
ORDER BY s.id ASC;
"@

& $mysql -u $user --password=$pass -B -e $sqlShows | ForEach-Object { $_ -replace "\t", "," } | Out-File -FilePath "$exportDir\02_Shows_Schedule.csv" -Encoding UTF8

# 3. Seats Inventory
$sqlSeats = @"
USE cinebook;
SELECT 
    st.id AS Seat_ID,
    s.movie_name AS Movie,
    CONCAT(st.row_label, st.seat_no) AS Seat_Code,
    st.row_label AS Row_Letter,
    st.seat_no AS Seat_Number,
    st.seat_type AS Tier,
    CASE 
        WHEN st.seat_type = 'REGULAR' THEN 150
        WHEN st.seat_type = 'PREMIUM' THEN 250
        WHEN st.seat_type = 'VIP' THEN 400
    END AS Price_Rs,
    IF(bs.active = 1, 'BOOKED', 'AVAILABLE') AS Current_Status
FROM seats st
JOIN shows s ON st.show_id = s.id
LEFT JOIN booking_seats bs ON st.id = bs.seat_id AND bs.active = 1
ORDER BY s.id ASC, st.row_label ASC, st.seat_no ASC;
"@

& $mysql -u $user --password=$pass -B -e $sqlSeats | ForEach-Object { $_ -replace "\t", "," } | Out-File -FilePath "$exportDir\03_Seats_Inventory.csv" -Encoding UTF8

# 4. Coupons
$sqlCoupons = @"
USE cinebook;
SELECT 
    code AS Coupon_Code,
    CONCAT(discount_percent, '%') AS Discount,
    IF(active, 'YES', 'NO') AS Is_Active
FROM coupons;
"@

& $mysql -u $user --password=$pass -B -e $sqlCoupons | ForEach-Object { $_ -replace "\t", "," } | Out-File -FilePath "$exportDir\04_Coupons.csv" -Encoding UTF8

Write-Host "Export completed to $exportDir"
