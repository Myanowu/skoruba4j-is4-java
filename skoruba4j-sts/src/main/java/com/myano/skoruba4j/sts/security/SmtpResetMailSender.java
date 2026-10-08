package com.myano.skoruba4j.sts.security;

import com.myano.skoruba4j.sts.config.IdserverProperties;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

public final class SmtpResetMailSender implements ResetMailSender {
  private static final Logger LOG = LoggerFactory.getLogger(SmtpResetMailSender.class);
  private final IdserverProperties.Smtp smtp;

  public SmtpResetMailSender(IdserverProperties.Smtp smtp) {
    this.smtp = smtp == null ? new IdserverProperties.Smtp() : smtp;
  }

  @Override
  public void sendReset(String to, String subject, String body) {
    if (to == null || to.isBlank()) {
      return;
    }
    String host = smtp.getHost();
    if (host == null || host.isBlank()) {
      LOG.info("Password reset email skipped (idserver.smtp.host is empty)");
      return;
    }
    try {
      JavaMailSenderImpl sender = new JavaMailSenderImpl();
      sender.setHost(host.trim());
      sender.setPort(smtp.getPort() <= 0 ? 587 : smtp.getPort());
      if (smtp.getUsername() != null && !smtp.getUsername().isBlank()) {
        sender.setUsername(smtp.getUsername());
        sender.setPassword(smtp.getPassword() == null ? "" : smtp.getPassword());
      }
      var props = sender.getJavaMailProperties();
      props.put("mail.smtp.auth", smtp.getUsername() != null && !smtp.getUsername().isBlank());
      props.put("mail.smtp.starttls.enable", smtp.isStartTls());
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
      String from = smtp.getFrom() == null || smtp.getFrom().isBlank() ? smtp.getUsername() : smtp.getFrom();
      if (from != null && !from.isBlank()) {
        helper.setFrom(from);
      }
      helper.setTo(to);
      helper.setSubject(subject == null ? "Reset password" : subject);
      helper.setText(body == null ? "" : body, false);
      sender.send(message);
    } catch (Exception ex) {
      LOG.warn("Password reset email could not be sent");
    }
  }
}
