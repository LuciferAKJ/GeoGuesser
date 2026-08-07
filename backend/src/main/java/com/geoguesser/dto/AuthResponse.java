package com.geoguesser.dto;

public class AuthResponse {

    private String token;
    private Long playerId;
    private String username;
    private String message;

    public AuthResponse() {}

    public AuthResponse(String token, Long playerId, String username, String message) {
        this.token = token;
        this.playerId = playerId;
        this.username = username;
        this.message = message;
    }

    public String getToken() {
        return token;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public String getUsername() {
        return username;
    }

    public String getMessage() {
        return message;
    }
}