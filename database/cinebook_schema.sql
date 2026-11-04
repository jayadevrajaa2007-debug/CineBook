-- =============================================================================
-- CineBook Database Schema & Initial Seed
-- Target: MySQL 8.0+ / MySQL Workbench
--
-- NOTE: Run this script directly in MySQL Workbench OR let the Spring Boot
-- application execute Flyway migrations automatically. DO NOT RUN BOTH.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS cinebook
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE cinebook;

-- Drop existing tables in reverse dependency order if resetting
-- DROP TABLE IF EXISTS booking_seats;
-- DROP TABLE IF EXISTS bookings;
-- DROP TABLE IF EXISTS seats;
-- DROP TABLE IF EXISTS shows;
-- DROP TABLE IF EXISTS coupons;

-- 1. Shows Table
CREATE TABLE IF NOT EXISTS shows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    movie_name VARCHAR(150) NOT NULL,
    show_time TIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Seats Table (5 rows A-E x 8 seats = 40 seats per show)
CREATE TABLE IF NOT EXISTS seats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    show_id BIGINT NOT NULL,
    row_label VARCHAR(1) NOT NULL,
    seat_no INT NOT NULL,
    seat_type VARCHAR(20) NOT NULL,
    CONSTRAINT uk_show_row_seat UNIQUE (show_id, row_label, seat_no),
    CONSTRAINT fk_seats_show FOREIGN KEY (show_id) REFERENCES shows (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_seats_show_id ON seats (show_id);

-- 3. Bookings Table (AUTO_INCREMENT starts at 1001 for BK1001 format)
CREATE TABLE IF NOT EXISTS bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    show_id BIGINT NOT NULL,
    coupon_code VARCHAR(50) NULL,
    subtotal DECIMAL(10, 2) NOT NULL,
    discount_amount DECIMAL(10, 2) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    refund_amount DECIMAL(10, 2) NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    cancelled_at DATETIME NULL,
    CONSTRAINT chk_booking_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    CONSTRAINT fk_bookings_show FOREIGN KEY (show_id) REFERENCES shows (id)
) ENGINE=InnoDB AUTO_INCREMENT=1001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_bookings_show_id ON bookings (show_id);

-- 4. Booking Seats Table
-- Generated column ensures at database level that an active seat cannot be double-booked.
CREATE TABLE IF NOT EXISTS booking_seats (
    booking_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    active_seat_id BIGINT GENERATED ALWAYS AS (IF(active, seat_id, NULL)) STORED,
    PRIMARY KEY (booking_id, seat_id),
    CONSTRAINT uk_active_seat UNIQUE (active_seat_id),
    CONSTRAINT fk_booking_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_seats_seat FOREIGN KEY (seat_id) REFERENCES seats (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_booking_seats_seat_id ON booking_seats (seat_id);
CREATE INDEX idx_booking_seats_booking_id ON booking_seats (booking_id);

-- 5. Coupons Table
CREATE TABLE IF NOT EXISTS coupons (
    code VARCHAR(50) PRIMARY KEY,
    discount_percent INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- SEED DATA
-- =============================================================================

-- Seed Shows
INSERT INTO shows (id, movie_name, show_time) VALUES
(1, 'Interstellar', '19:30:00'),
(2, 'Inception', '16:00:00'),
(3, 'The Dark Knight', '22:00:00')
ON DUPLICATE KEY UPDATE 
    movie_name = VALUES(movie_name),
    show_time = VALUES(show_time);

-- Seed Seats (40 per show = 120 total)
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

-- Seed Coupon
INSERT INTO coupons (code, discount_percent, active) VALUES
('CINE10', 10, true)
ON DUPLICATE KEY UPDATE 
    discount_percent = VALUES(discount_percent),
    active = VALUES(active);

