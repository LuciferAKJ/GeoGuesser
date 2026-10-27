package com.geoguesser.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoguesser.exception.EmailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResendEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final String apiKey;
    private final String fromAddress;
    private final String frontendBaseUrl;
    private final boolean verificationRequired;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ResendEmailService(
            @Value("${resend.api-key:}") String apiKey,
            @Value("${resend.from:GeoGuesser <onboarding@resend.dev>}") String fromAddress,
            @Value("${app.frontend-base-url:http://localhost:5500}") String frontendBaseUrl,
            @Value("${app.email-verification-required:true}") boolean verificationRequired,
            ObjectMapper objectMapper
    ) {
        this(apiKey, fromAddress, frontendBaseUrl, verificationRequired, objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    public ResendEmailService(
            String apiKey,
            String fromAddress,
            String frontendBaseUrl,
            boolean verificationRequired,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.fromAddress = (fromAddress != null && !fromAddress.isBlank()) ? fromAddress.trim() : "GeoGuesser <onboarding@resend.dev>";
        this.frontendBaseUrl = (frontendBaseUrl != null && !frontendBaseUrl.isBlank()) ? frontendBaseUrl.trim() : "http://localhost:5500";
        this.verificationRequired = verificationRequired;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;

        if (this.verificationRequired && this.apiKey.isEmpty()) {
            log.warn("Resend API key is not configured. Email verification is enabled and will reject registrations until RESEND_API_KEY is provided.");
        }
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    @Override
    public boolean isVerificationRequired() {
        return verificationRequired;
    }

    @Override
    public void sendVerificationEmail(String toEmail, String username, String rawToken) {
        if (!verificationRequired) {
            log.info("Email verification is disabled in current configuration. Skipping email dispatch to {}", maskEmail(toEmail));
            return;
        }

        if (apiKey.isEmpty()) {
            log.error("Cannot dispatch verification email: RESEND_API_KEY environment variable is not configured.");
            throw new IllegalStateException("Email service is not configured. Please contact the administrator.");
        }

        String verificationUrl = buildVerificationUrl(rawToken);
        String htmlBody = buildHtmlTemplate(username, verificationUrl);

        Map<String, Object> payload = new HashMap<>();
        payload.put("from", fromAddress);
        payload.put("to", List.of(toEmail));
        payload.put("subject", "Verify your GeoGuesser account");
        payload.put("html", htmlBody);

        try {
            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_API_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Verification email successfully dispatched via Resend to {}", maskEmail(toEmail));
            } else {
                String errorDetail = extractErrorMessage(response.body(), response.statusCode());
                log.error("Resend API returned HTTP {}: {}", response.statusCode(), errorDetail);
                throw new EmailDeliveryException("Failed to send verification email: " + errorDetail);
            }
        } catch (IOException e) {
            log.error("Network I/O failure while communicating with Resend API: {}", e.getMessage());
            throw new EmailDeliveryException("Unable to connect to email delivery service. Please try again later.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while communicating with Resend API: {}", e.getMessage());
            throw new EmailDeliveryException("Email delivery request was interrupted.", e);
        }
    }

    private String buildVerificationUrl(String rawToken) {
        String base = frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
        return base + "/#verify-token=" + rawToken;
    }

    private String extractErrorMessage(String responseBody, int statusCode) {
        if (responseBody == null || responseBody.isBlank()) {
            return "HTTP " + statusCode;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("message")) {
                return root.get("message").asText();
            }
            if (root.has("error")) {
                return root.get("error").asText();
            }
        } catch (Exception ignored) {}
        return "HTTP " + statusCode;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }

    private String buildHtmlTemplate(String username, String verificationUrl) {
        String safeName = (username != null && !username.isBlank()) ? username : "Explorer";
        return "<!DOCTYPE html>"
                + "<html lang=\"en\">"
                + "<head><meta charset=\"UTF-8\"><title>Verify your GeoGuesser Account</title></head>"
                + "<body style=\"margin:0;padding:0;background-color:#0b1220;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#ffffff;\">"
                + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"background-color:#0b1220;padding:40px 16px;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"max-width:540px;background-color:#111927;border-radius:12px;border:1px solid #1e293b;padding:36px;box-shadow:0 12px 32px rgba(0,0,0,0.5);\">"
                + "<tr><td style=\"text-align:center;padding-bottom:24px;\">"
                + "<h1 style=\"margin:0;font-size:26px;font-weight:800;color:#3b82f6;letter-spacing:-0.5px;\">🌍 GeoGuesser</h1>"
                + "<p style=\"margin:6px 0 0;font-size:14px;color:#94a3b8;\">Global Satellite Geography Game</p>"
                + "</td></tr>"
                + "<tr><td style=\"font-size:16px;line-height:24px;color:#e2e8f0;padding-bottom:20px;\">"
                + "Hello <strong>" + escapeHtml(safeName) + "</strong>,"
                + "<br><br>"
                + "Thank you for creating an account on GeoGuesser! Please verify your email address to activate your account and start playing."
                + "</td></tr>"
                + "<tr><td align=\"center\" style=\"padding:20px 0 28px;\">"
                + "<a href=\"" + verificationUrl + "\" style=\"display:inline-block;padding:14px 32px;background:linear-gradient(135deg,#3b82f6 0%,#2563eb 100%);color:#ffffff;font-size:15px;font-weight:700;text-decoration:none;border-radius:8px;box-shadow:0 4px 14px rgba(37,99,235,0.4);\">"
                + "Verify My Email"
                + "</a>"
                + "</td></tr>"
                + "<tr><td style=\"font-size:13px;line-height:20px;color:#94a3b8;border-top:1px solid #1e293b;padding-top:20px;\">"
                + "This verification link will expire in <strong>30 minutes</strong>. If you did not create this account, no further action is required."
                + "<br><br>"
                + "Having trouble with the button? Copy and paste this link into your browser:<br>"
                + "<span style=\"color:#60a5fa;word-break:break-all;font-size:12px;\">" + verificationUrl + "</span>"
                + "</td></tr>"
                + "</table>"
                + "</td></tr>"
                + "</table>"
                + "</body></html>";
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
