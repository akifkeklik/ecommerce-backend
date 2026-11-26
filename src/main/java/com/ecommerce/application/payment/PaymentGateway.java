package com.ecommerce.application.payment;

import com.ecommerce.domain.payment.PaymentMethod;

import java.math.BigDecimal;

/**
 * The payment provider, seen from the application. A real provider (Stripe, iyzico, ...) is another
 * implementation of this interface; nothing else in checkout changes.
 */
public interface PaymentGateway {

    /** Stored on each payment so it is clear which provider took the money. */
    String name();

    ChargeResult charge(ChargeRequest request);

    RefundResult refund(String providerTransactionId, BigDecimal amount, String currency);

    /** {@code paymentToken} comes from the provider's client-side form; card numbers never reach this API. */
    record ChargeRequest(String reference, BigDecimal amount, String currency, PaymentMethod method,
            String paymentToken) {
    }

    record ChargeResult(boolean approved, String providerTransactionId, String cardLastFour,
            String failureCode, String failureReason) {

        public static ChargeResult approved(String providerTransactionId, String cardLastFour) {
            return new ChargeResult(true, providerTransactionId, cardLastFour, null, null);
        }

        public static ChargeResult declined(String failureCode, String failureReason) {
            return new ChargeResult(false, null, null, failureCode, failureReason);
        }
    }

    record RefundResult(boolean succeeded, String providerRefundId, String failureReason) {
    }
}
