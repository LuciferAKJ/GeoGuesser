package com.geoguesser.dto;

public class MessageResponse {

    private String message;

    public MessageResponse() {}

    public MessageResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}