package com.ecommerce.application.dto.payment;

import com.ecommerce.domain.payment.Payment;
import com.ecommerce.domain.payment.PaymentMethod;
import com.ecommerce.domain.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentDto(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        PaymentStatus status,
        String transactionId,
        String provider,
        String cardLastFour,
        String failureCode,
        String failureReason,
        BigDecimal refundAmount,
        LocalDateTime processedAt) {

    public static PaymentDto from(Payment p) {
        return new PaymentDto(p.getId(), p.getOrder().getId(), p.getAmount(), p.getCurrency(), p.getMethod(),
                p.getStatus(), p.getTransactionId(), p.getProvider(), p.getCardLastFour(), p.getFailureCode(),
                p.getFailureReason(), p.getRefundAmount(), p.getProcessedAt());
    }
}
