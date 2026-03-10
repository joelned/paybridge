package com.paybridge.Services.impl;

import com.paybridge.Services.EmailProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@Primary
@Profile("resend")
public class ResendEmailService implements EmailProvider {

    private static final Logger logger = LoggerFactory.getLogger(ResendEmailService.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;


    public ResendEmailService(RestTemplate restTemplate,
                              @Value("${resend.base-url:https://api.resend.com}") String baseUrl,
                              @Value("${resend.api-key}") String apiKey,
                              @Value("${resend.from.email}") String fromEmail,
                              @Value("${resend.from.name:PayBridge}") String fromName) {
        this.restTemplate = restTemplate;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = normalizeSecret(apiKey);
        this.fromEmail = fromEmail != null ? fromEmail.trim() : fromEmail;
        this.fromName = fromName;
    }

    @Async
    @Override
    public void sendVerificationEmail(String toEmail, String verificationCode, String businessName) {
        sendEmail(toEmail, "Verify Your PayBridge Account", buildVerificationEmail(verificationCode, businessName));
    }

    @Async
    @Override
    public void sendPasswordResetEmail(String toEmail, String resetCode, String businessName) {
        sendEmail(toEmail, "Reset Your PayBridge Password", buildPasswordResetEmail(resetCode, businessName));
    }

    private void sendEmail(String toEmail, String subject, String htmlContent) {
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("Resend API key not configured; skipping email send");
            return;
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            logger.warn("Resend sender email not configured; skipping email send");
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setBearerAuth(apiKey);

            Map<String, Object> payload = Map.of(
                    "from", fromName + " <" + fromEmail + ">",
                    "to", List.of(toEmail),
                    "subject", subject,
                    "html", htmlContent
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            HttpStatusCode status = restTemplate.postForEntity(
                    baseUrl + "/emails",
                    request,
                    String.class
            ).getStatusCode();

            if (!status.is2xxSuccessful()) {
                throw new RuntimeException("Resend email send failed with status: " + status.value());
            }
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            logger.error("Resend API rejected email request with status {} and body: {}",
                    ex.getStatusCode().value(),
                    body == null || body.isBlank() ? "<empty>" : body);
            throw new RuntimeException("Error sending email via Resend", ex);
        } catch (RestClientException ex) {
            throw new RuntimeException("Error sending email via Resend", ex);
        }
    }

    private String normalizeBaseUrl(String value) {
        if (value == null) {
            return "https://api.resend.com";
        }
        String trimmed = value.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String normalizeSecret(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String buildVerificationEmail(String verificationCode, String businessName) {
        String greeting = businessName != null ? "Hello " + businessName + "," : "Hello,";

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }\n" +
                "        .container { max-width: 600px; margin: 0 auto; padding: 20px; }\n" +
                "        .header { background: linear-gradient(135deg, #6366f1, #8b5cf6); padding: 30px; text-align: center; color: white; }\n" +
                "        .code { font-size: 32px; font-weight: bold; letter-spacing: 5px; color: #6366f1; text-align: center; margin: 30px 0; }\n" +
                "        .footer { margin-top: 30px; padding-top: 20px; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <h1>PayBridge</h1>\n" +
                "            <p>Payment Orchestration Platform</p>\n" +
                "        </div>\n" +
                "        \n" +
                "        <h2>Verify Your Email Address</h2>\n" +
                "        <p>" + greeting + "</p>\n" +
                "        <p>Thank you for registering with PayBridge. To complete your registration and start using our payment orchestration platform, please verify your email address using the code below:</p>\n" +
                "        \n" +
                "        <div class=\"code\">" + verificationCode + "</div>\n" +
                "        \n" +
                "        <p>This verification code will expire in <strong>10 minutes</strong>.</p>\n" +
                "        \n" +
                "        <p>If you didn't create an account with PayBridge, please ignore this email.</p>\n" +
                "        \n" +
                "        <div class=\"footer\">\n" +
                "            <p>Best regards,<br>The PayBridge Team</p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }

    private String buildPasswordResetEmail(String resetCode, String businessName) {
        String greeting = businessName != null ? "Hello " + businessName + "," : "Hello,";

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }\n" +
                "        .container { max-width: 600px; margin: 0 auto; padding: 20px; }\n" +
                "        .header { background: linear-gradient(135deg, #0f766e, #0ea5e9); padding: 30px; text-align: center; color: white; }\n" +
                "        .code { font-size: 32px; font-weight: bold; letter-spacing: 5px; color: #0f766e; text-align: center; margin: 30px 0; }\n" +
                "        .footer { margin-top: 30px; padding-top: 20px; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <h1>PayBridge</h1>\n" +
                "            <p>Password Reset</p>\n" +
                "        </div>\n" +
                "        \n" +
                "        <h2>Reset Your Password</h2>\n" +
                "        <p>" + greeting + "</p>\n" +
                "        <p>Use the code below to reset your PayBridge account password:</p>\n" +
                "        <div class=\"code\">" + resetCode + "</div>\n" +
                "        <p>This reset code will expire in <strong>15 minutes</strong>.</p>\n" +
                "        <p>If you did not request a password reset, please ignore this email.</p>\n" +
                "        <div class=\"footer\">\n" +
                "            <p>Best regards,<br>The PayBridge Team</p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }
}
