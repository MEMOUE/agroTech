package com.agrotech.agroparcelles.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Map;

/**
 * Requête d'extraction satellitaire : un polygone GeoJSON (Feature ou geometry)
 * et une période optionnelle. Transmis tel quel au service Python.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExtractionRequest {

    @NotNull(message = "Le GeoJSON du polygone est obligatoire")
    @Schema(description = "GeoJSON Feature ou geometry de type Polygon",
            example = "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[11.50,3.84],[11.51,3.84],[11.51,3.85],[11.50,3.85],[11.50,3.84]]]}}")
    private Map<String, Object> geoJson;

    @Schema(description = "Date de début (yyyy-MM-dd)", example = "2026-06-01")
    private String dateDebut;

    @Schema(description = "Date de fin (yyyy-MM-dd)", example = "2026-06-29")
    private String dateFin;
}
