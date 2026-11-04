-- =============================================================================
-- CineBook Schema Migration V1: Initial Schema
-- MySQL 8.0+, InnoDB, utf8mb4
-- =============================================================================

CREATE TABLE IF NOT EXISTS shows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    movie_name VARCHAR(150) NOT NULL,
    show_time TIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

CREATE TABLE IF NOT EXISTS coupons (
    code VARCHAR(50) PRIMARY KEY,
    discount_percent INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

