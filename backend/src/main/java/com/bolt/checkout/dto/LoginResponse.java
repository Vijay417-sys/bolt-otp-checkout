package com.bolt.checkout.dto;

public class LoginResponse {
    private boolean success;
    private Long userId;
    private String firstName;
    private String lastName;
    private String sessionToken;

    /**
     * The freshly rotated login code, returned so the client can display it.
     *
     * <p>The code is rotated on every successful login, so a code that leaked from the
     * registration screen stops working the moment it is first used. The assignment
     * specifies that codes are shown on screen and never emailed or texted, so this
     * response is the only channel by which the replacement code can reach the user -
     * without it, a second login would be impossible.
     */
    private String nextCode;

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

    public LoginResponse(boolean success, Long userId, String firstName, String lastName, String sessionToken, String nextCode) {
        this(success, userId, firstName, lastName, sessionToken);
        this.nextCode = nextCode;
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
    public String getNextCode() { return nextCode; }
    public void setNextCode(String nextCode) { this.nextCode = nextCode; }
}