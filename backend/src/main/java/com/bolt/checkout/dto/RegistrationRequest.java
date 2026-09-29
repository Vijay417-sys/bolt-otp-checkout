package com.bolt.checkout.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration input.
 *
 * <p>The email setter normalises the value on the way in so that Bean Validation and the
 * persistence layer both observe the canonical form. This is the first of two normalisation
 * points; {@code AuthService.normalizeEmail} re-applies it before every database lookup.
 */
public class RegistrationRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    private String lastName;

    public String getEmail() { return email; }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
}