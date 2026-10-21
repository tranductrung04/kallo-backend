package com.healthcare.kallo.service.impl;

import com.healthcare.kallo.model.User;
import com.healthcare.kallo.config.AuthProperties;
import com.healthcare.kallo.service.EmailService;
import org.slf4j.*;
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

  public MailEmailService(ObjectProvider<JavaMailSender> sender,
      @Value("${MAIL_FROM:no-reply@kallo.local}") String from,
      AuthProperties authProperties) {
    this.sender = sender;
    this.from = from;
    this.otpExpiration = authProperties.otpExpiration();
  }

  @Override
  @Async("mailTaskExecutor")
  public void sendVerificationCode(User user, String code) {
    send(user, "Xác minh email Kallo", "Mã xác minh email của bạn là: " + code);
  }

  @Override
  @Async("mailTaskExecutor")
  public void sendPasswordResetCode(User user, String code) {
    send(user, "Đặt lại mật khẩu Kallo", "Mã đặt lại mật khẩu của bạn là: " + code);
  }

  private void send(User user, String subject, String body) {
    try {
      JavaMailSender mailSender = sender.getIfAvailable();
      if (mailSender == null) {
        log.warn("Mail sender is not configured; authentication email was not sent for user {}", user.getId());
        return;
      }
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(from);
      message.setTo(user.getEmail());
      message.setSubject(subject);
      message.setText(body + "\nMã có hiệu lực trong " + formatDuration(otpExpiration)
          + ". Nếu không phải bạn, hãy bỏ qua thư này.");
      mailSender.send(message);
    } catch (RuntimeException e) {
      log.warn("Unable to send authentication email for user {}", user.getId(), e);
    }
  }

  private String formatDuration(Duration duration) {
    long minutes = duration.toMinutes();
    return minutes > 0 ? minutes + " phút" : duration.toSeconds() + " giây";
  }
}
