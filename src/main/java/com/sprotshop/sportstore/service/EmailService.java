package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.entity.Order;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

@Service
@Slf4j
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail; // ✅ lấy email gửi từ config

    @Async
    public void sendOtpEmail(String toEmail, String otp) {
        try {
            if (toEmail == null || toEmail.isBlank()) return;

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            Context context = new Context();
            context.setVariable("otp", otp);
            context.setVariable("userEmail", toEmail);
            context.setVariable("expiryMinutes", 10);
            String htmlContent = templateEngine.process("otp-reset", context);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Mã Xác Thực Reset Mật Khẩu - SportStore");
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("OTP email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Error sending OTP email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    @Async
    public void sendHtmlEmail(String toEmail, String subject, String template, Context context) {
        try {
            if (toEmail == null || toEmail.isBlank()) return;

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String htmlContent = templateEngine.process(template, context);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("HTML email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Error sending HTML email [{}] to {}: {}", subject, toEmail, e.getMessage(), e);
        }
    }

    @Async
    public void sendSimpleTextEmail(String toEmail, String subject, String text) {
        try {
            if (toEmail == null || toEmail.isBlank()) return;

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(text);

            mailSender.send(mimeMessage);
            log.info("Text email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Error sending text email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    @Async
    public void sendOrderConfirmation(String toEmail, Order order) {
        try {
            if (toEmail == null || toEmail.isBlank()) return;

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            Context context = new Context(Locale.forLanguageTag("vi_VN"));
            context.setVariable("order", order);
            context.setVariable("items", order.getOrderItems());
            context.setVariable("total", order.getTotalAmount());
            context.setVariable("status", order.getStatus().name());
            String htmlContent = templateEngine.process("order-confirmation", context);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Xác Nhận Đơn Hàng #" + order.getId() + " - SportStore");
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("Order confirmation sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Error sending order confirmation for order {} to {}: {}", order.getId(), toEmail, e.getMessage(), e);
        }
    }

    @Async
    public void sendStatusUpdate(String toEmail, Order order, String oldStatus, String newStatus) {
        try {
            if (toEmail == null || toEmail.isBlank()) return;

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            Context context = new Context(Locale.forLanguageTag("vi_VN"));
            context.setVariable("order", order);
            context.setVariable("oldStatus", oldStatus);
            context.setVariable("newStatus", newStatus);
            String htmlContent = templateEngine.process("order-status-update", context);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Cập Nhật Trạng Thái Đơn Hàng #" + order.getId() + " - SportStore");
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("Status update sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Error sending status update email to {}: {}", toEmail, e.getMessage(), e);
        }
    }
}
