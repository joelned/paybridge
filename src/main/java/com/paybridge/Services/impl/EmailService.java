package com.paybridge.Services.impl;

import com.paybridge.Services.EmailProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@Profile("smtp")
public class EmailService implements EmailProvider {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

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
        try {
            // In test or non-configured environments, skip sending to avoid failing flows
            if (fromEmail == null || fromEmail.isBlank()) {
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);

        } catch (RuntimeException | MessagingException runtimeException) {
            throw new RuntimeException("Error sending email", runtimeException);
        }
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
