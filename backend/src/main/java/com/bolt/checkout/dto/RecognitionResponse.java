package com.bolt.checkout.dto;

public class RecognitionResponse {
    private boolean registered;

    public RecognitionResponse(boolean registered) {
        this.registered = registered;
    }

    public boolean isRegistered() { return registered; }
    public void setRegistered(boolean registered) { this.registered = registered; }
}