package com.geoguesser.dto;

import jakarta.validation.constraints.NotBlank;

public class VerifyTokenRequest {

    @NotBlank(message = "Verification token is required")
    private String token;

    public VerifyTokenRequest() {}

    public VerifyTokenRequest(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
