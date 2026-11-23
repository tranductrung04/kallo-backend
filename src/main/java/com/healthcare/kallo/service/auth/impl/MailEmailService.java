package com.healthcare.kallo.service.auth.impl;

import com.healthcare.kallo.config.AuthProperties;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.service.auth.EmailService;
import com.healthcare.kallo.service.auth.MailMetrics;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MailEmailService implements EmailService {
  private static final Logger log = LoggerFactory.getLogger(MailEmailService.class);
  private static final String BRAND_COLOR = "#7B4FE0";
  private static final String ACCENT_COLOR = "#7B4FE0";
  private static final int MAX_ATTEMPTS = 3;

  private final ObjectProvider<JavaMailSender> sender;
  private final String from;
  private final Duration otpExpiration;
  private final MailMetrics metrics;

  public MailEmailService(ObjectProvider<JavaMailSender> sender,
                          @Value("${MAIL_FROM:no-reply@kallo.local}") String from,
                          AuthProperties authProperties, MailMetrics metrics) {
    this.sender = sender;
    this.from = from;
    this.otpExpiration = authProperties.otpExpiration();
    this.metrics = metrics;
  }

  @Override
  @Async("mailTaskExecutor")
  public void sendVerificationCode(User user, String code) {
    sendWithRetry(user, "verification", "Xác minh email Kallo", "Xác minh email",
            "Dùng mã dưới đây để hoàn tất xác minh tài khoản Kallo.", code);
  }

  @Override
  @Async("mailTaskExecutor")
  public void sendPasswordResetCode(User user, String code) {
    sendWithRetry(user, "password-reset", "Đặt lại mật khẩu Kallo", "Đặt lại mật khẩu",
            "Dùng mã dưới đây để tạo lại mật khẩu Kallo của bạn.", code);
  }

  private void sendWithRetry(User user, String type, String subject, String title,
                             String intro, String code) {
    RuntimeException failure = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        JavaMailSender mailSender = sender.getIfAvailable();
        if (mailSender == null) {
          // Cấu hình thiếu: thử lại cũng không giải quyết được
          failure = new IllegalStateException("Mail sender is not configured");
          break;
        }
        MimeMessage message = mailSender.createMimeMessage();
        // Multipart bắt buộc để dùng setText(plain, html)
        MimeMessageHelper helper = new MimeMessageHelper(message,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(user.getEmail());
        helper.setSubject(subject);
        helper.setText(plainText(user, intro, code), htmlBody(user, title, intro, code));
        mailSender.send(message);
        metrics.recordSent(type);
        return;
      } catch (MailException e) {
        // Lỗi mạng/SMTP: có thể thử lại
        failure = e;
      } catch (MessagingException e) {
        // Lỗi dựng email: thử lại không có tác dụng
        failure = new IllegalStateException("Unable to build authentication email", e);
        break;
      } catch (RuntimeException e) {
        // Lỗi lập trình/cấu hình: thử lại không có tác dụng
        failure = e;
        break;
      }
      if (attempt < MAX_ATTEMPTS) {
        try {
          Thread.sleep((long) Math.pow(2, attempt - 1) * 250
                  + ThreadLocalRandom.current().nextLong(100));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          failure = new IllegalStateException("Mail retry interrupted", interrupted);
          break;
        }
      }
    }
    metrics.recordFailed(type);
    log.warn("Unable to send authentication email for user {}", user.getId(), failure);
  }

  private String plainText(User user, String intro, String code) {
    return "Xin chào " + recipientName(user) + ",\n\n"
            + intro + "\n\n"
            + "Mã xác thực của bạn: " + code + "\n"
            + "Mã có hiệu lực trong " + formatDuration(otpExpiration) + ".\n\n"
            + "Nếu bạn không yêu cầu thao tác này, hãy bỏ qua email này.\n\n"
            + "Kallo\nSức khỏe mỗi ngày, tốt hơn mỗi ngày.";
  }

  private String htmlBody(User user, String title, String intro, String code) {
    String name = escapeHtml(recipientName(user));
    String safeCode = escapeHtml(code);
    String safeTitle = escapeHtml(title);
    String safeIntro = escapeHtml(intro);
    String expiration = escapeHtml(formatDuration(otpExpiration));
    return """
        <!doctype html>
        <html lang="vi">
        <body style="margin:0;padding:0;background:#f4f7fb;color:#243447;font-family:Arial,Helvetica,sans-serif;">
          <div style="display:none;max-height:0;overflow:hidden;opacity:0;">
            %s — mã có hiệu lực trong %s.
          </div>
          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background:#f4f7fb;">
            <tr>
              <td align="center" style="padding:32px 16px;">
                <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="width:100%%;max-width:600px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 8px 28px rgba(23,63,95,.10);">
                  <tr>
                    <td style="padding:28px 32px;background:%s;color:#ffffff;">
                      <div style="font-size:24px;font-weight:700;letter-spacing:1px;">KALLO</div>
                      <div style="margin-top:6px;font-size:13px;color:#e4dbfa;">Sức khỏe mỗi ngày, tốt hơn mỗi ngày.</div>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:40px 32px 32px;">
                      <p style="margin:0 0 10px;color:#6b7c93;font-size:14px;">Xin chào %s,</p>
                      <h1 style="margin:0 0 14px;color:%s;font-size:28px;line-height:1.25;">%s</h1>
                      <p style="margin:0;color:#526579;font-size:16px;line-height:1.65;">%s</p>
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="margin:28px 0 20px;background:#f3effd;border:1px solid #dcd0f7;border-radius:12px;">
                        <tr>
                          <td align="center" style="padding:24px 16px;">
                            <div style="margin-bottom:8px;color:#6b5a9e;font-size:12px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;">MÃ XÁC THỰC</div>
                            <div style="color:%s;font-size:36px;font-weight:700;letter-spacing:8px;line-height:1.2;">%s</div>
                          </td>
                        </tr>
                      </table>
                      <p style="margin:0;color:#526579;font-size:14px;line-height:1.6;">Mã có hiệu lực trong <strong>%s</strong>. Không chia sẻ mã này với bất kỳ ai.</p>
                      <div style="height:1px;margin:28px 0;background:#e7edf3;"></div>
                      <p style="margin:0;color:#7a8b9d;font-size:13px;line-height:1.6;">Nếu bạn không yêu cầu email này, tài khoản của bạn vẫn an toàn. Bạn có thể bỏ qua email này.</p>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:22px 32px;background:#f8fafc;border-top:1px solid #edf1f5;">
                      <p style="margin:0;color:#6b7c93;font-size:12px;line-height:1.6;">Email tự động từ Kallo. Vui lòng không trả lời email này.</p>
                      <p style="margin:6px 0 0;color:#9aa8b6;font-size:12px;">© Kallo · Sức khỏe mỗi ngày, tốt hơn mỗi ngày.</p>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
        """.formatted(safeIntro, expiration, BRAND_COLOR, name, BRAND_COLOR, safeTitle, safeIntro,
            ACCENT_COLOR, safeCode, expiration);
  }

  private String recipientName(User user) {
    return user.getDisplayName() == null || user.getDisplayName().isBlank()
            ? "bạn"
            : user.getDisplayName();
  }

  private String escapeHtml(String value) {
    return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
  }

  private String formatDuration(Duration duration) {
    long minutes = duration.toMinutes();
    return minutes > 0 ? minutes + " phút" : duration.toSeconds() + " giây";
  }
}