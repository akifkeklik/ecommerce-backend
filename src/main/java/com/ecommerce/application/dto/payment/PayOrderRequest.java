package com.ecommerce.application.dto.payment;

import com.ecommerce.domain.payment.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code paymentToken} is issued by the provider's client-side form; this API never sees card numbers. */
public record PayOrderRequest(
        @NotNull PaymentMethod method,
        @Size(max = 200) String paymentToken) {
}
