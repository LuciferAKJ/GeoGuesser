package com.geoguesser.dto;

public class MessageResponse {

    private String message;
    private Boolean verificationRequired;

    public MessageResponse() {}

    public MessageResponse(String message) {
        this.message = message;
        this.verificationRequired = false;
    }

    public MessageResponse(String message, Boolean verificationRequired) {
        this.message = message;
        this.verificationRequired = verificationRequired;
    }

    public String getMessage() {
        return message;
    }

    public Boolean isVerificationRequired() {
        return verificationRequired;
    }

    public Boolean getVerificationRequired() {
        return verificationRequired;
    }
}