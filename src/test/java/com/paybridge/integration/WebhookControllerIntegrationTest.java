package com.paybridge.integration;

import com.paybridge.Services.WebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookService webhookService;

    @Test
    void stripeWebhook_ValidSignature_ReturnsOk() throws Exception {
        when(webhookService.handleStripeWebhook(anyString(), anyString()))
                .thenReturn(Map.of("processed", true, "eventId", "evt_123"));

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .content("{\"id\":\"evt_123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processed").value(true));
    }

    @Test
    void stripeWebhook_MissingSignature_ReturnsBadRequest() throws Exception {
        when(webhookService.handleStripeWebhook(anyString(), isNull()))
                .thenThrow(new IllegalArgumentException("Missing Stripe-Signature header"));

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt_123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("Missing Stripe-Signature header")));
    }

    @Test
    void paystackWebhook_InvalidSignature_ReturnsBadRequest() throws Exception {
        when(webhookService.handlePaystackWebhook(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Invalid Paystack webhook signature"));

        mockMvc.perform(post("/api/v1/webhooks/paystack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-paystack-signature", "invalid")
                        .content("{\"event\":\"charge.success\",\"data\":{\"reference\":\"ref_1\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("Invalid Paystack webhook signature")));
    }
}
