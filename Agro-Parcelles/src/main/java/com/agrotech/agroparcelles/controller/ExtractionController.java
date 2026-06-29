package com.agrotech.agroparcelles.controller;

import com.agrotech.agroparcelles.dto.request.ExtractionRequest;
import com.agrotech.agroparcelles.service.SentinelExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/extraction")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@Tag(name = "Extraction satellite", description = "Envoi d'un polygone GeoJSON au service d'extraction Sentinel-2")
public class ExtractionController {

    private final SentinelExtractionService sentinelExtractionService;

    @Operation(
        summary = "Extraire les indices Sentinel-2 d'un polygone",
        description = "Transmet le polygone GeoJSON au microservice Python d'extraction et " +
                      "retourne les indices satellitaires (NDVI, couverture nuageuse, superficie...). " +
                      "Le polygone n'a pas besoin d'être enregistré au préalable."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Indices satellitaires retournés"),
        @ApiResponse(responseCode = "400", description = "GeoJSON invalide", content = @Content),
        @ApiResponse(responseCode = "502", description = "Service d'extraction indisponible", content = @Content)
    })
    @PostMapping
    public ResponseEntity<Map<String, Object>> extraire(@Valid @RequestBody ExtractionRequest request) {
        return ResponseEntity.ok(sentinelExtractionService.extraire(request));
    }
}
