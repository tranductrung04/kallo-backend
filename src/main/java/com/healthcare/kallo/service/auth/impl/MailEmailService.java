package com.healthcare.kallo.service.auth.impl;

import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.config.AuthProperties;
import com.healthcare.kallo.service.auth.EmailService;
import com.healthcare.kallo.service.auth.MailMetrics;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.*;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;

@Service
public class MailEmailService implements EmailService {
  private static final Logger log = LoggerFactory.getLogger(MailEmailService.class);
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
    sendWithRetry(user, "verification", "Xác minh email Kallo", "Mã xác minh email của bạn là: " + code);
  }

  @Override
  @Async("mailTaskExecutor")
  public void sendPasswordResetCode(User user, String code) {
    sendWithRetry(user, "password-reset", "Đặt lại mật khẩu Kallo", "Mã đặt lại mật khẩu của bạn là: " + code);
  }

  private void sendWithRetry(User user, String type, String subject, String body) {
    RuntimeException failure = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        JavaMailSender mailSender = sender.getIfAvailable();
        if (mailSender == null) {
          failure = new IllegalStateException("Mail sender is not configured");
        } else {
          SimpleMailMessage message = new SimpleMailMessage();
          message.setFrom(from);
          message.setTo(user.getEmail());
          message.setSubject(subject);
          message.setText(body + "\nMã có hiệu lực trong " + formatDuration(otpExpiration)
              + ". Nếu không phải bạn, hãy bỏ qua thư này.");
          mailSender.send(message);
          metrics.recordSent(type);
          return;
        }
      } catch (MailException e) {
        failure = e;
      } catch (RuntimeException e) {
        failure = e;
      }
      if (attempt < 3) {
        try {
          Thread.sleep((long) Math.pow(2, attempt - 1) * 250 + ThreadLocalRandom.current().nextLong(100));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          failure = new IllegalStateException("Mail retry interrupted", interrupted);
          break;
        }
      }
    }
    metrics.recordFailed(type);
    log.warn("Unable to send authentication email for user {} after retries", user.getId(), failure);
  }

  private String formatDuration(Duration duration) {
    long minutes = duration.toMinutes();
    return minutes > 0 ? minutes + " phút" : duration.toSeconds() + " giây";
  }
}
