package com.aluminum.manager.inventory;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PaymentRequestTest {
    @Test
    void existingPaymentPayloadMayOmitLockedMonthConfirmation() {
        var mapper = JsonMapper.builder().build();
        var ordinary = mapper.readValue("""
                {"amount":1000000,"paymentMethod":"CASH","paymentDate":"2026-03-25T00:00:00"}
                """, StockDtos.PaymentRequest.class);
        assertNull(ordinary.confirmLockedCarryoverMonth());

        var confirmed = mapper.readValue("""
                {"amount":1000000,"paymentMethod":"CASH","confirmLockedCarryoverMonth":true}
                """, StockDtos.PaymentRequest.class);
        assertTrue(confirmed.confirmLockedCarryoverMonth());
    }
}
