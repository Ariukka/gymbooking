package com.example.gymbooking.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreatePaymentRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeBookingAsScalarId() throws Exception {
        String json = """
                {
                  "booking": 42,
                  "amount": 12000
                }
                """;

        CreatePaymentRequest request = objectMapper.readValue(json, CreatePaymentRequest.class);

        assertEquals(42L, request.resolveBookingId());
    }

    @Test
    void shouldDeserializeAmountFromTotalPriceAlias() throws Exception {
        String json = """
                {
                  "bookingId": 9,
                  "totalPrice": 15000.50
                }
                """;

        CreatePaymentRequest request = objectMapper.readValue(json, CreatePaymentRequest.class);

        assertEquals(9L, request.resolveBookingId());
        assertEquals("15000.5", request.getAmount().toPlainString());
    }
}
