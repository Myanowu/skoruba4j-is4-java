package com.myano.skoruba4j.sts.security;

public interface ResetMailSender {
  void sendReset(String to, String subject, String body);
}
