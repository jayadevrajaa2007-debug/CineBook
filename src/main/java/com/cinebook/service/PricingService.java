package com.cinebook.service;

import com.cinebook.model.Coupon;
import com.cinebook.model.Seat;
import com.cinebook.repo.CouponRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for calculating subtotals, coupon discounts, and final totals.
 * Demonstrates method overloading:
 * - calculateTotal(List<Seat>)
 * - calculateTotal(List<Seat>, String)
 *
 * Seat prices are read exclusively from SeatType.getPrice().
 */
@Service
public class PricingService {

    private final CouponRepository couponRepository;

    public PricingService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    /**
     * Overloaded method 1: Calculates total without a coupon.
     *
     * @param seats List of selected seats
     * @return PricingResult with subtotal and total
     */
    public PricingResult calculateTotal(List<Seat> seats) {
        return calculateTotal(seats, null);
    }

    /**
     * Overloaded method 2: Calculates total with an optional coupon code.
     *
     * @param seats List of selected seats
     * @param couponCode Raw coupon code (trimmed, case-insensitive)
     * @return PricingResult with breakdown and discount applied (if valid)
     */
    public PricingResult calculateTotal(List<Seat> seats, String couponCode) {
        if (seats == null || seats.isEmpty()) {
            BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return new PricingResult(zero, zero, zero, false, null, 0, null);
        }

        // Calculate subtotal strictly using SeatType.getPrice()
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Seat seat : seats) {
            BigDecimal seatPrice = BigDecimal.valueOf(seat.getSeatType().getPrice());
            subtotal = subtotal.add(seatPrice);
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

        // Check coupon if provided
        if (couponCode == null || couponCode.trim().isEmpty()) {
            return new PricingResult(subtotal, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    subtotal, false, null, 0, null);
        }

        String normalizedCode = couponCode.trim().toUpperCase();
        Optional<Coupon> couponOpt = couponRepository.findByCodeIgnoreCase(normalizedCode);

        if (couponOpt.isEmpty()) {
            String message = "Coupon '" + couponCode.trim() + "' is invalid. Booking proceeded without discount.";
            return new PricingResult(subtotal, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    subtotal, false, normalizedCode, 0, message);
        }

        Coupon coupon = couponOpt.get();
        if (!coupon.isActive()) {
            String message = "Coupon '" + coupon.getCode() + "' is inactive/expired. Booking proceeded without discount.";
            return new PricingResult(subtotal, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    subtotal, false, coupon.getCode(), 0, message);
        }

        // Valid coupon: calculate discount
        int percent = coupon.getDiscountPercent();
        BigDecimal discount = subtotal.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal finalTotal = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        String message = "Coupon '" + coupon.getCode() + "' applied: " + percent + "% discount!";

        return new PricingResult(subtotal, discount, finalTotal, true, coupon.getCode(), percent, message);
    }
}

