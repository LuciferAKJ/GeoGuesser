package com.geoguesser.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public class ResendVerificationRequest {

    @JsonAlias({"usernameOrEmail", "email", "username"})
    @NotBlank(message = "Email or username is required")
    private String emailOrUsername;

    public ResendVerificationRequest() {}

    public ResendVerificationRequest(String emailOrUsername) {
        this.emailOrUsername = emailOrUsername != null ? emailOrUsername.trim() : null;
    }

    public String getEmailOrUsername() {
        return emailOrUsername;
    }

    public void setEmailOrUsername(String emailOrUsername) {
        this.emailOrUsername = emailOrUsername != null ? emailOrUsername.trim() : null;
    }
}
