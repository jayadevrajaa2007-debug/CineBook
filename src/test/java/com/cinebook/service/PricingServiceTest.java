package com.cinebook.service;

import com.cinebook.model.Coupon;
import com.cinebook.model.Seat;
import com.cinebook.model.SeatType;
import com.cinebook.model.Show;
import com.cinebook.repo.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests 3 & 4:
 * 3. C3 + C4 with CINE10 = Rs. 450.00; without a coupon = Rs. 500.00.
 * 4. Invalid or inactive coupons do not block booking and apply no discount.
 */
@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private PricingService pricingService;

    private Show show;
    private Seat seatC3;
    private Seat seatC4;

    @BeforeEach
    void setUp() {
        show = new Show(1L, "Interstellar", LocalTime.of(19, 30));
        // Rows C-D are PREMIUM seats priced at Rs. 250 each
        seatC3 = new Seat(101L, show, "C", 3, SeatType.PREMIUM);
        seatC4 = new Seat(102L, show, "C", 4, SeatType.PREMIUM);
    }

    @Test
    @DisplayName("Test 3a: C3 + C4 without a coupon = Rs. 500.00 (Overloaded method 1)")
    void testCalculateTotalWithoutCoupon() {
        List<Seat> seats = List.of(seatC3, seatC4);

        PricingResult result = pricingService.calculateTotal(seats);

        assertNotNull(result);
        assertEquals(new BigDecimal("500.00"), result.getSubtotal());
        assertEquals(new BigDecimal("0.00"), result.getDiscountAmount());
        assertEquals(new BigDecimal("500.00"), result.getTotalAmount());
        assertFalse(result.isCouponApplied());
        assertNull(result.getCouponCode());
    }

    @Test
    @DisplayName("Test 3b: C3 + C4 with CINE10 = Rs. 450.00 (Overloaded method 2, 10% discount)")
    void testCalculateTotalWithCine10() {
        List<Seat> seats = List.of(seatC3, seatC4);
        when(couponRepository.findByCodeIgnoreCase("CINE10"))
                .thenReturn(Optional.of(new Coupon("CINE10", 10, true)));

        PricingResult result = pricingService.calculateTotal(seats, "CINE10");

        assertNotNull(result);
        assertEquals(new BigDecimal("500.00"), result.getSubtotal());
        assertEquals(new BigDecimal("50.00"), result.getDiscountAmount());
        assertEquals(new BigDecimal("450.00"), result.getTotalAmount());
        assertTrue(result.isCouponApplied());
        assertEquals("CINE10", result.getCouponCode());
        assertEquals(10, result.getDiscountPercent());
    }

    @Test
    @DisplayName("Verify coupon codes are trimmed and case-insensitive (e.g. ' cine10 ')")
    void testCouponCaseInsensitiveAndTrimmed() {
        List<Seat> seats = List.of(seatC3, seatC4);
        when(couponRepository.findByCodeIgnoreCase("CINE10"))
                .thenReturn(Optional.of(new Coupon("CINE10", 10, true)));

        PricingResult result = pricingService.calculateTotal(seats, "  cine10  ");

        assertTrue(result.isCouponApplied());
        assertEquals(new BigDecimal("450.00"), result.getTotalAmount());
        assertEquals(new BigDecimal("50.00"), result.getDiscountAmount());
    }

    @Test
    @DisplayName("Test 4a: Invalid coupon does not block booking and applies no discount")
    void testInvalidCouponDoesNotBlock() {
        List<Seat> seats = List.of(seatC3, seatC4);
        when(couponRepository.findByCodeIgnoreCase("UNKNOWN50"))
                .thenReturn(Optional.empty());

        PricingResult result = pricingService.calculateTotal(seats, "UNKNOWN50");

        assertNotNull(result);
        assertEquals(new BigDecimal("500.00"), result.getSubtotal());
        assertEquals(new BigDecimal("0.00"), result.getDiscountAmount());
        assertEquals(new BigDecimal("500.00"), result.getTotalAmount());
        assertFalse(result.isCouponApplied());
        assertNotNull(result.getCouponMessage());
        assertTrue(result.getCouponMessage().contains("invalid"));
    }

    @Test
    @DisplayName("Test 4b: Inactive coupon does not block booking and applies no discount")
    void testInactiveCouponDoesNotBlock() {
        List<Seat> seats = List.of(seatC3, seatC4);
        Coupon inactiveCoupon = new Coupon("EXPIRED20", 20, false); // active = false
        when(couponRepository.findByCodeIgnoreCase("EXPIRED20"))
                .thenReturn(Optional.of(inactiveCoupon));

        PricingResult result = pricingService.calculateTotal(seats, "EXPIRED20");

        assertNotNull(result);
        assertEquals(new BigDecimal("500.00"), result.getSubtotal());
        assertEquals(new BigDecimal("0.00"), result.getDiscountAmount());
        assertEquals(new BigDecimal("500.00"), result.getTotalAmount());
        assertFalse(result.isCouponApplied());
        assertNotNull(result.getCouponMessage());
        assertTrue(result.getCouponMessage().contains("inactive"));
    }

    @Test
    @DisplayName("Verify pricing for mixed seat tiers (Regular + Premium + VIP)")
    void testMixedTierPricing() {
        Seat regSeat = new Seat(1L, show, "A", 1, SeatType.REGULAR); // 150
        Seat premSeat = new Seat(2L, show, "C", 1, SeatType.PREMIUM); // 250
        Seat vipSeat = new Seat(3L, show, "E", 1, SeatType.VIP); // 400

        List<Seat> seats = List.of(regSeat, premSeat, vipSeat); // Total = 150 + 250 + 400 = 800

        PricingResult result = pricingService.calculateTotal(seats);

        assertEquals(new BigDecimal("800.00"), result.getSubtotal());
        assertEquals(new BigDecimal("800.00"), result.getTotalAmount());
    }
}

