package com.agrotech.agroauth.controller;

import com.agrotech.agroauth.dto.*;
import com.agrotech.agroauth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Inscription, connexion et validation de token JWT")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Créer un compte", description = "Enregistre un nouvel utilisateur et retourne un token JWT")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Compte créé avec succès",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Données invalides (validation échouée)", content = @Content),
        @ApiResponse(responseCode = "409", description = "Nom d'utilisateur ou email déjà utilisé", content = @Content)
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(summary = "Se connecter", description = "Authentifie un utilisateur et retourne un token JWT valide 24h")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Connexion réussie",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Identifiants invalides", content = @Content),
        @ApiResponse(responseCode = "403", description = "Compte désactivé", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(
        summary = "Valider un token JWT",
        description = "Utilisé par les autres microservices. Passer le token dans le header Authorization: Bearer <token>"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token valide",
            content = @Content(schema = @Schema(implementation = TokenValidationResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token invalide ou expiré", content = @Content)
    })
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(TokenValidationResponse.builder().valid(false).build());
        }
        String token = authHeader.substring(7);
        TokenValidationResponse response = authService.validateToken(token);
        return response.isValid()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @Operation(summary = "Mon profil", description = "Retourne le profil de l'utilisateur connecté")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profil retourné",
            content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
        @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content)
    })
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile(Principal principal) {
        return ResponseEntity.ok(authService.getProfile(principal.getName()));
    }
}
