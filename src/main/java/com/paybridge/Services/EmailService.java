package com.paybridge.Services;

import com.paybridge.Models.Entities.Users;
import org.apache.catalina.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendVerificationEmail(String toEmail, String verificationCode, String businessName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Verify Your PayBridge Account");

            String emailContent = buildVerificationEmail(verificationCode, businessName);
            helper.setText(emailContent, true);

            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send verification email", e);
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
                "        <p>This verification code will expire in <strong>15 minutes</strong>.</p>\n" +
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
}