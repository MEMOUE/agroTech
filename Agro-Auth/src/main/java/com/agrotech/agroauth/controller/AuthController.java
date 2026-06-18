package com.agrotech.agroauth.controller;

import com.agrotech.agroauth.dto.*;
import com.agrotech.agroauth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Endpoint appelé par les autres microservices pour valider un token JWT.
     * Exemple : GET /api/auth/validate?token=eyJhbGciOi...
     */
    @GetMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validateToken(@RequestParam String token) {
        TokenValidationResponse response = authService.validateToken(token);
        return response.isValid()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
}