package com.bolt.checkout.dto;

public class LoginResponse {
    private boolean success;
    private Long userId;
    private String firstName;
    private String lastName;
    private String sessionToken;

    public LoginResponse(boolean success, Long userId, String firstName, String lastName) {
        this.success = success;
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public LoginResponse(boolean success, Long userId, String firstName, String lastName, String sessionToken) {
        this(success, userId, firstName, lastName);
        this.sessionToken = sessionToken;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
}
