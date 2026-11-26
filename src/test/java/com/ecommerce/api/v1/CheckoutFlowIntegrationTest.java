package com.ecommerce.api.v1;

import com.ecommerce.BaseIntegrationTest;
import com.ecommerce.domain.order.OrderStatus;
import com.ecommerce.domain.payment.PaymentStatus;
import com.ecommerce.domain.product.Product;
import com.ecommerce.domain.product.ProductStatus;
import com.ecommerce.domain.promotion.Discount;
import com.ecommerce.domain.promotion.DiscountType;
import com.ecommerce.domain.user.Role;
import com.ecommerce.domain.user.User;
import com.ecommerce.infrastructure.repository.DiscountRepository;
import com.ecommerce.infrastructure.repository.OrderRepository;
import com.ecommerce.infrastructure.repository.PaymentRepository;
import com.ecommerce.infrastructure.repository.ProductRepository;
import com.ecommerce.infrastructure.repository.UserRepository;
import com.ecommerce.infrastructure.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole purchase through the HTTP API on the real Spring context (H2): discount, declined and retried
 * payment, cancellation with refund, then review moderation and helpful votes.
 */
class CheckoutFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private DiscountRepository discountRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private ObjectMapper json;

    private String customerToken;
    private String otherToken;
    private String adminToken;
    private Product product;
    private String code;

    @BeforeEach
    void data() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        customerToken = token(user("customer-" + run, Role.CUSTOMER));
        otherToken = token(user("other-" + run, Role.CUSTOMER));
        adminToken = token(user("admin-" + run, Role.ADMIN));

        product = new Product();
        product.setName("Notebook " + run);
        product.setSlug("notebook-" + run);
        product.setSku("NB-" + run);
        product.setPrice(new BigDecimal("50.00"));
        product.setStockQuantity(10);
        product.setStatus(ProductStatus.ACTIVE);
        product = productRepository.save(product);

        code = "SAVE10-" + run;
        Discount discount = new Discount();
        discount.setCode(code);
        discount.setType(DiscountType.PERCENTAGE);
        discount.setValue(new BigDecimal("10"));
        discount.setUsageLimit(1);
        discountRepository.save(discount);
    }

    private User user(String name, Role role) {
        User u = new User();
        u.setUsername(name);
        u.setEmail(name + "@example.com");
        u.setPassword("not-used-by-jwt-tests");
        u.setRole(role);
        return userRepository.save(u);
    }

    private String token(User u) {
        return "Bearer " + tokens.generateAccessToken(u.getUsername());
    }

    private ResultActions postJson(String url, String bearer, String body) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String orderBody(int quantity, String discountCode) {
        return """
                {"items":[{"productId":"%s","quantity":%d}],
                 "shippingAddress":{"streetAddress":"1 Main St","city":"Izmir","state":"Izmir","postalCode":"35000","country":"TR"},
                 "discountCode":%s}
                """.formatted(product.getId(), quantity, discountCode == null ? "null" : "\"" + discountCode + "\"");
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Order with a code, declined then approved payment, cancellation refunds and restores everything")
    void purchaseLifecycle() throws Exception {
        JsonNode order = body(postJson("/api/v1/orders", customerToken, orderBody(2, code.toLowerCase()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountAmount").value(10.00))
                .andExpect(jsonPath("$.total").value(90.00)));
        String orderId = order.get("id").asText();

        // The code had one use left; a second order with it is refused and reserves nothing.
        postJson("/api/v1/orders", customerToken, orderBody(1, code)).andExpect(status().isBadRequest());
        assertThat(productRepository.findById(product.getId()).orElseThrow().getReservedQuantity()).isEqualTo(2);

        String pay = "/api/v1/orders/" + orderId + "/payments";
        postJson(pay, otherToken, "{\"method\":\"CREDIT_CARD\",\"paymentToken\":\"tok_visa\"}")
                .andExpect(status().isForbidden());
        postJson(pay, customerToken, "{\"method\":\"CREDIT_CARD\",\"paymentToken\":\"tok_decline\"}")
                .andExpect(status().isPaymentRequired());
        postJson(pay, customerToken, "{\"method\":\"CREDIT_CARD\",\"paymentToken\":\"tok_visa\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(90.00));
        // Paying again does not charge again.
        postJson(pay, customerToken, "{\"method\":\"CREDIT_CARD\",\"paymentToken\":\"tok_visa\"}")
                .andExpect(status().isOk());

        UUID id = UUID.fromString(orderId);
        assertThat(orderRepository.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        Product sold = productRepository.findById(product.getId()).orElseThrow();
        assertThat(sold.getStockQuantity()).isEqualTo(8);
        assertThat(sold.getReservedQuantity()).isZero();

        postJson("/api/v1/orders/" + orderId + "/cancel?reason=changed", customerToken, "{}")
                .andExpect(status().isOk());

        assertThat(orderRepository.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(paymentRepository.findByOrderId(id).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(discountRepository.findByCode(code).orElseThrow().getUsageCount()).isZero();
    }

    @Test
    @DisplayName("Unknown codes are refused before any stock is reserved")
    void unknownCode_isRefused() throws Exception {
        postJson("/api/v1/orders", customerToken, orderBody(1, "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("does not exist")));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("A review is hidden until an admin approves it, and each reader can vote helpful once")
    void reviewModeration() throws Exception {
        String reviewId = body(postJson("/api/v1/reviews", customerToken,
                "{\"productId\":\"" + product.getId() + "\",\"rating\":5,\"title\":\"Good\",\"comment\":\"Solid paper\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verifiedPurchase").value(false))).get("id").asText();

        postJson("/api/v1/reviews", customerToken,
                "{\"productId\":\"" + product.getId() + "\",\"rating\":4}").andExpect(status().isConflict());

        String publicList = "/api/v1/reviews/product/" + product.getId();
        mockMvc.perform(get(publicList)).andExpect(jsonPath("$.totalReviews").value(0));

        postJson("/api/v1/reviews/" + reviewId + "/approve", customerToken, "").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reviews/pending").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + reviewId + "')]").exists());
        postJson("/api/v1/reviews/" + reviewId + "/approve", adminToken, "").andExpect(status().isOk());

        mockMvc.perform(get(publicList))
                .andExpect(jsonPath("$.totalReviews").value(1))
                .andExpect(jsonPath("$.averageRating").value(5.0));

        String helpful = "/api/v1/reviews/" + reviewId + "/helpful";
        postJson(helpful, otherToken, "").andExpect(status().isOk());
        postJson(helpful, otherToken, "").andExpect(status().isConflict());
        postJson(helpful, customerToken, "").andExpect(status().isBadRequest());
        mockMvc.perform(get(publicList)).andExpect(jsonPath("$.content[0].helpfulCount").value(1));
    }
}
