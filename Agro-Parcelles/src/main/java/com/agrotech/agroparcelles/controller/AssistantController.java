package com.agrotech.agroparcelles.controller;

import com.agrotech.agroparcelles.dto.reponse.ChatResponse;
import com.agrotech.agroparcelles.dto.reponse.PestPhotoAnalysisResponse;
import com.agrotech.agroparcelles.dto.request.ChatRequest;
import com.agrotech.agroparcelles.service.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@Tag(name = "Assistant IA", description = "Chat conversationnel et analyse de photo (maladies/ravageurs). " +
        "Ni le chat ni l'analyse de photo ne sont un diagnostic certain : voir le champ 'avertissement'.")
public class AssistantController {

    private static final long TAILLE_MAX_PHOTO_OCTETS = 8L * 1024 * 1024;
    private static final Set<String> TYPES_IMAGE_ACCEPTES = Set.of("image/jpeg", "image/png", "image/webp");

    private final AssistantService assistantService;

    @Operation(summary = "Chat général avec l'assistant IA",
               description = "Pas de parcelle précise : utilisé par le widget de chat flottant hors fiche parcelle.")
    @PostMapping("/api/assistant/chat")
    public ResponseEntity<ChatResponse> chatGeneral(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(ChatResponse.builder().reponse(assistantService.chatGeneral(request)).build());
    }

    @Operation(summary = "Chat avec l'assistant IA sur une parcelle",
               description = "Le contexte (nom, superficie, cultures en cours, recommandations) de la parcelle " +
                             "est transmis automatiquement à l'assistant.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Réponse de l'assistant"),
        @ApiResponse(responseCode = "404", description = "Parcelle introuvable", content = @Content),
        @ApiResponse(responseCode = "502", description = "Assistant IA indisponible", content = @Content)
    })
    @PostMapping("/api/parcelles/{id}/assistant/chat")
    public ResponseEntity<ChatResponse> chatParcelle(@PathVariable Long id, @Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(ChatResponse.builder().reponse(assistantService.chatParcelle(id, request)).build());
    }

    @Operation(summary = "Analyser une photo de la culture (signes de maladie/ravageur)",
               description = "Upload d'une photo (jpg/png/webp, 8 Mo max). Réponse structurée : observations, " +
                             "hypothèses avec niveau de confiance, recommandation, avertissement. " +
                             "Ce n'est PAS un diagnostic : confirmer auprès d'un agronome avant tout traitement.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Analyse de la photo retournée"),
        @ApiResponse(responseCode = "400", description = "Fichier manquant, trop volumineux ou type non supporté", content = @Content),
        @ApiResponse(responseCode = "404", description = "Parcelle introuvable", content = @Content),
        @ApiResponse(responseCode = "502", description = "Assistant IA indisponible", content = @Content)
    })
    @PostMapping(value = "/api/parcelles/{id}/assistant/pest-photo", consumes = "multipart/form-data")
    public ResponseEntity<PestPhotoAnalysisResponse> analyserPhoto(
            @PathVariable Long id,
            @RequestParam("photo") MultipartFile photo) {
        if (photo.isEmpty()) {
            throw new IllegalArgumentException("Aucune photo reçue.");
        }
        if (photo.getSize() > TAILLE_MAX_PHOTO_OCTETS) {
            throw new IllegalArgumentException("Photo trop volumineuse (8 Mo maximum).");
        }
        String mimeType = photo.getContentType();
        if (mimeType == null || !TYPES_IMAGE_ACCEPTES.contains(mimeType)) {
            throw new IllegalArgumentException("Type de fichier non supporté (jpg, png ou webp attendu).");
        }

        String imageB64;
        try {
            imageB64 = Base64.getEncoder().encodeToString(photo.getBytes());
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Photo illisible : " + e.getMessage());
        }

        return ResponseEntity.ok(assistantService.analyserPhoto(id, imageB64, mimeType));
    }
}
