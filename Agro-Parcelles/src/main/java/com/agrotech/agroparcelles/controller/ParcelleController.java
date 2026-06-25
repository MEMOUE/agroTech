package com.agrotech.agroparcelles.controller;

import com.agrotech.agroparcelles.dto.reponse.ParcelleDtoReponse;
import com.agrotech.agroparcelles.dto.reponse.ParcelleMapResponse;
import com.agrotech.agroparcelles.dto.reponse.SatelliteDataResponse;
import com.agrotech.agroparcelles.dto.request.ParcelleDtoRequest;
import com.agrotech.agroparcelles.service.ParcelleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/parcelles")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@Tag(name = "Parcelles", description = "Gestion des parcelles agricoles avec données satellite")
public class ParcelleController {

    private final ParcelleService parcelleService;

    @Operation(summary = "Récupérer une parcelle par ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Parcelle trouvée",
            content = @Content(schema = @Schema(implementation = ParcelleDtoReponse.class))),
        @ApiResponse(responseCode = "404", description = "Parcelle introuvable", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<ParcelleDtoReponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(parcelleService.findById(id));
    }

    @Operation(summary = "Récupérer toutes les parcelles d'un utilisateur")
    @GetMapping("/utilisateur/{idUser}")
    public ResponseEntity<List<ParcelleDtoReponse>> findByIdUser(@PathVariable Long idUser) {
        return ResponseEntity.ok(parcelleService.findByIdUser(idUser));
    }

    @Operation(
        summary = "Enregistrer une parcelle",
        description = "Crée une nouvelle parcelle à partir des coordonnées GPS des sommets. " +
                      "La superficie (ha), le périmètre (m) et le centroïde sont calculés automatiquement."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Parcelle créée",
            content = @Content(schema = @Schema(implementation = ParcelleDtoReponse.class))),
        @ApiResponse(responseCode = "400", description = "Données invalides (< 3 sommets, coordonnées hors limites)", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ParcelleDtoReponse> create(@Valid @RequestBody ParcelleDtoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parcelleService.save(request));
    }

    @Operation(summary = "Mettre à jour une parcelle")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Parcelle mise à jour"),
        @ApiResponse(responseCode = "404", description = "Parcelle introuvable", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<ParcelleDtoReponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ParcelleDtoRequest request) {
        return ResponseEntity.ok(parcelleService.update(id, request));
    }

    @Operation(summary = "Supprimer une parcelle")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Parcelle supprimée"),
        @ApiResponse(responseCode = "404", description = "Parcelle introuvable", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        parcelleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Visualisation satellite de la parcelle",
        description = "Retourne le GeoJSON du polygone, le bounding box, le centroïde, " +
                      "le niveau de zoom recommandé et les URLs des tuiles satellite (Esri, OSM, Google Maps)."
    )
    @ApiResponse(responseCode = "200", description = "Données carte retournées",
        content = @Content(schema = @Schema(implementation = ParcelleMapResponse.class)))
    @GetMapping("/{id}/map")
    public ResponseEntity<ParcelleMapResponse> getMapData(@PathVariable Long id) {
        return ResponseEntity.ok(parcelleService.getMapData(id));
    }

    @Operation(
        summary = "Données agro-climatiques satellite (NASA POWER)",
        description = "Récupère les données journalières NASA POWER pour le centroïde de la parcelle : " +
                      "température (°C), précipitations (mm/j), humidité relative (%), " +
                      "rayonnement solaire (kWh/m²/j), vitesse du vent (m/s). " +
                      "Par défaut : 30 derniers jours."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Données satellite retournées",
            content = @Content(schema = @Schema(implementation = SatelliteDataResponse.class))),
        @ApiResponse(responseCode = "502", description = "Erreur API NASA POWER", content = @Content)
    })
    @GetMapping("/{id}/satellite")
    public ResponseEntity<SatelliteDataResponse> getSatelliteData(
            @PathVariable Long id,
            @Parameter(description = "Date début (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @Parameter(description = "Date fin (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {

        SatelliteDataResponse response = (debut != null && fin != null)
                ? parcelleService.getSatelliteData(id, debut, fin)
                : parcelleService.getSatelliteData(id);

        return ResponseEntity.ok(response);
    }
}
