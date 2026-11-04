package com.cinebook.service;

import java.math.BigDecimal;

/**
 * Encapsulates the pricing breakdown calculated by PricingService.
 */
public class PricingResult {

    private final BigDecimal subtotal;
    private final BigDecimal discountAmount;
    private final BigDecimal totalAmount;
    private final boolean couponApplied;
    private final String couponCode;
    private final int discountPercent;
    private final String couponMessage;

    public PricingResult(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal totalAmount,
                         boolean couponApplied, String couponCode, int discountPercent, String couponMessage) {
        this.subtotal = subtotal;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
        this.couponApplied = couponApplied;
        this.couponCode = couponCode;
        this.discountPercent = discountPercent;
        this.couponMessage = couponMessage;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public boolean isCouponApplied() {
        return couponApplied;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public int getDiscountPercent() {
        return discountPercent;
    }

    public String getCouponMessage() {
        return couponMessage;
    }

    @Override
    public String toString() {
        return "PricingResult{" +
                "subtotal=" + subtotal +
                ", discountAmount=" + discountAmount +
                ", totalAmount=" + totalAmount +
                ", couponApplied=" + couponApplied +
                ", couponCode='" + couponCode + '\'' +
                ", discountPercent=" + discountPercent +
                ", couponMessage='" + couponMessage + '\'' +
                '}';
    }
}

