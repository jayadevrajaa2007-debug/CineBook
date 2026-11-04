package com.cinebook.model;

/**
 * Enumeration of seat types and their prices.
 * Prices are strictly encapsulated here and exposed via getPrice().
 * Row mapping:
 * - Rows A, B: REGULAR (Rs. 150)
 * - Rows C, D: PREMIUM (Rs. 250)
 * - Row E: VIP (Rs. 400)
 */
public enum SeatType {
    REGULAR(150),
    PREMIUM(250),
    VIP(400);

    private final int price;

    SeatType(int price) {
        this.price = price;
    }

    public int getPrice() {
        return price;
    }

    /**
     * Maps a row letter to the corresponding SeatType.
     *
     * @param rowLabel String containing the row letter (e.g. "A", "b")
     * @return corresponding SeatType
     * @throws IllegalArgumentException if rowLabel is invalid
     */
    public static SeatType fromRowLabel(String rowLabel) {
        if (rowLabel == null || rowLabel.trim().isEmpty()) {
            throw new IllegalArgumentException("Row label cannot be null or empty");
        }
        char row = Character.toUpperCase(rowLabel.trim().charAt(0));
        switch (row) {
            case 'A':
            case 'B':
                return REGULAR;
            case 'C':
            case 'D':
                return PREMIUM;
            case 'E':
                return VIP;
            default:
                throw new IllegalArgumentException("Invalid row label: '" + rowLabel + "'. Must be A, B, C, D, or E.");
        }
    }
}

