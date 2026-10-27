package com.geoguesser.service;

public interface EmailService {

    void sendVerificationEmail(String toEmail, String username, String rawToken);

    boolean isConfigured();

    boolean isVerificationRequired();
}
