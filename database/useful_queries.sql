-- =============================================================================
-- CineBook Useful Queries for MySQL Workbench Inspection
-- =============================================================================

USE cinebook;

-- 1. All Shows with Seat Counts
SELECT 
    s.id AS show_id,
    s.movie_name,
    TIME_FORMAT(s.show_time, '%h:%i %p') AS formatted_time,
    COUNT(st.id) AS total_seats
FROM shows s
LEFT JOIN seats st ON s.id = st.show_id
GROUP BY s.id, s.movie_name, s.show_time
ORDER BY s.id ASC;

-- 2. Booked Seats per Show (Only Active / Confirmed Bookings)
SELECT 
    s.id AS show_id,
    s.movie_name,
    CONCAT(st.row_label, st.seat_no) AS seat_code,
    st.seat_type,
    b.id AS booking_id,
    CONCAT('BK', b.id) AS formatted_booking_id,
    b.status AS booking_status
FROM shows s
JOIN seats st ON s.id = st.show_id
JOIN booking_seats bs ON st.id = bs.seat_id
JOIN bookings b ON bs.booking_id = b.id
WHERE bs.active = TRUE AND b.status = 'CONFIRMED'
ORDER BY s.id, st.row_label, st.seat_no;

-- 3. All Bookings with Their Associated Seats (JOIN)
SELECT 
    b.id AS booking_id,
    CONCAT('BK', b.id) AS display_id,
    b.status,
    s.movie_name,
    TIME_FORMAT(s.show_time, '%h:%i %p') AS show_time,
    GROUP_CONCAT(CONCAT(st.row_label, st.seat_no) ORDER BY st.row_label, st.seat_no SEPARATOR ', ') AS seats,
    b.subtotal,
    b.coupon_code,
    b.discount_amount,
    b.total_amount,
    b.refund_amount,
    b.created_at,
    b.cancelled_at
FROM bookings b
JOIN shows s ON b.show_id = s.id
JOIN booking_seats bs ON b.id = bs.booking_id
JOIN seats st ON bs.seat_id = st.id
GROUP BY b.id, s.movie_name, s.show_time, b.status, b.subtotal, b.coupon_code, b.discount_amount, b.total_amount, b.refund_amount, b.created_at, b.cancelled_at
ORDER BY b.id DESC;

-- 4. Total Revenue of CONFIRMED Bookings
SELECT 
    COUNT(id) AS total_confirmed_bookings,
    COALESCE(SUM(total_amount), 0.00) AS total_revenue_rs
FROM bookings
WHERE status = 'CONFIRMED';

-- 5. Total Refunds Issued on CANCELLED Bookings
SELECT 
    COUNT(id) AS total_cancelled_bookings,
    COALESCE(SUM(refund_amount), 0.00) AS total_refunds_rs
FROM bookings
WHERE status = 'CANCELLED';

-- 6. Free (Available) Seats for a Specific Show (e.g., Show ID 1: Interstellar)
-- Using LEFT JOIN ... IS NULL to detect unoccupied seats
SELECT 
    st.id AS seat_id,
    CONCAT(st.row_label, st.seat_no) AS seat_code,
    st.row_label,
    st.seat_no,
    st.seat_type
FROM seats st
LEFT JOIN (
    SELECT bs.seat_id 
    FROM booking_seats bs
    JOIN bookings b ON bs.booking_id = b.id
    WHERE bs.active = TRUE AND b.status = 'CONFIRMED'
) occupied ON st.id = occupied.seat_id
WHERE st.show_id = 1
  AND occupied.seat_id IS NULL
ORDER BY st.row_label ASC, st.seat_no ASC;

