package com.bolt.checkout.controller;

import com.bolt.checkout.dto.LoginResponse;
import com.bolt.checkout.dto.RecognitionResponse;
import com.bolt.checkout.dto.RegistrationRequest;
import com.bolt.checkout.dto.RegistrationResponse;
import com.bolt.checkout.dto.VerifyOtpRequest;
import com.bolt.checkout.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Trims incoming strings before Bean Validation runs, so a value such as
     * "  vijay@example.com  " is normalised rather than rejected as malformed.
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    /**
     * Registers a user and returns the freshly generated 6-digit login code.
     * The code is shown on screen only - it is never emailed or texted.
     */
    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        RegistrationResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Background recognition check used by the checkout form while the user keeps typing.
     * Returns nothing beyond a boolean so no personal data leaks.
     */
    @GetMapping("/recognize")
    public ResponseEntity<RecognitionResponse> recognize(
            @RequestParam("email") @NotBlank @Email(message = "Invalid email address") String email) {
        return ResponseEntity.ok(authService.recognize(email));
    }

    @PostMapping("/verify")
    public ResponseEntity<LoginResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }
}