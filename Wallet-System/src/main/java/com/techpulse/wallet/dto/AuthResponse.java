package com.techpulse.wallet.dto;

public class AuthResponse {

    private String token;
    private String tokenType;
    private String email;
    private String role;

    public AuthResponse() {
    }

    public AuthResponse(String token, String tokenType,
                        String email, String role) {
        this.token = token;
        this.tokenType = tokenType;
        this.email = email;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}