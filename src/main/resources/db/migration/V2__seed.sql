-- =============================================================================
-- CineBook Schema Migration V2: Seed Data
-- 3 Shows, 40 Seats per Show (120 total), and CINE10 Coupon
-- =============================================================================

-- Seed 3 Shows (Idempotent)
INSERT INTO shows (id, movie_name, show_time) VALUES
(1, 'Interstellar', '19:30:00'),
(2, 'Inception', '16:00:00'),
(3, 'The Dark Knight', '22:00:00')
ON DUPLICATE KEY UPDATE 
    movie_name = VALUES(movie_name),
    show_time = VALUES(show_time);

-- Seed 40 Seats per Show using CROSS JOIN of derived tables
-- Rows A-B = REGULAR, Rows C-D = PREMIUM, Row E = VIP
INSERT INTO seats (show_id, row_label, seat_no, seat_type)
SELECT 
    s.id AS show_id,
    r.row_label,
    n.seat_no,
    CASE 
        WHEN r.row_label IN ('A', 'B') THEN 'REGULAR'
        WHEN r.row_label IN ('C', 'D') THEN 'PREMIUM'
        WHEN r.row_label = 'E' THEN 'VIP'
    END AS seat_type
FROM shows s
CROSS JOIN (
    SELECT 'A' AS row_label UNION ALL
    SELECT 'B' UNION ALL
    SELECT 'C' UNION ALL
    SELECT 'D' UNION ALL
    SELECT 'E'
) r
CROSS JOIN (
    SELECT 1 AS seat_no UNION ALL
    SELECT 2 UNION ALL
    SELECT 3 UNION ALL
    SELECT 4 UNION ALL
    SELECT 5 UNION ALL
    SELECT 6 UNION ALL
    SELECT 7 UNION ALL
    SELECT 8
) n
WHERE NOT EXISTS (
    SELECT 1 FROM seats existing 
    WHERE existing.show_id = s.id 
      AND existing.row_label = r.row_label 
      AND existing.seat_no = n.seat_no
);

-- Seed Coupon CINE10 (Idempotent)
INSERT INTO coupons (code, discount_percent, active) VALUES
('CINE10', 10, true)
ON DUPLICATE KEY UPDATE 
    discount_percent = VALUES(discount_percent),
    active = VALUES(active);

