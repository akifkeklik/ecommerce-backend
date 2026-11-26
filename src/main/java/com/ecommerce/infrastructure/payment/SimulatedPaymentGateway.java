package com.ecommerce.infrastructure.payment;

import com.ecommerce.application.payment.PaymentGateway;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A stand-in provider for development, tests and the demo. No money moves.
 * Test tokens: {@code tok_decline} is declined, {@code tok_insufficient_funds} fails for funds,
 * anything else is approved.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    @PostConstruct
    void warn() {
        log.warn("Payments use the SIMULATED provider: no real money is charged. Set app.payment.provider for a real one.");
    }

    @Override
    public String name() {
        return "simulated";
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String token = request.paymentToken() == null ? "" : request.paymentToken();
        return switch (token) {
            case "tok_decline" -> ChargeResult.declined("card_declined", "The card was declined.");
            case "tok_insufficient_funds" -> ChargeResult.declined("insufficient_funds", "Insufficient funds.");
            default -> ChargeResult.approved("sim_ch_" + UUID.randomUUID(), "4242");
        };
    }

    @Override
    public RefundResult refund(String providerTransactionId, BigDecimal amount, String currency) {
        return new RefundResult(true, "sim_re_" + UUID.randomUUID(), null);
    }
}
