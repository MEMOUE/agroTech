package com.agrotech.agroparcelles.controller;

import com.agrotech.agroparcelles.dto.reponse.HistoriqueParcelleResponse;
import com.agrotech.agroparcelles.dto.reponse.ParcelleDtoReponse;
import com.agrotech.agroparcelles.dto.reponse.ParcelleMapResponse;
import com.agrotech.agroparcelles.dto.reponse.RecommandationResponse;
import com.agrotech.agroparcelles.dto.reponse.SatelliteDataResponse;
import com.agrotech.agroparcelles.dto.reponse.SolDataResponse;
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
                      "rayonnement solaire (MJ/m²/j), vitesse du vent (m/s). " +
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

    @Operation(
        summary = "Composition du sol (nutriments)",
        description = "Estime pour le centroïde de la parcelle, sur 0-30 cm : pH, azote total, " +
                      "matière organique, capacité d'échange cationique et texture (argile/limon/sable), " +
                      "via SoilGrids (ISRIC). Chaque indicateur est accompagné d'un niveau et d'un conseil. " +
                      "Le phosphore et le potassium ne sont pas disponibles par satellite."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Composition du sol retournée",
            content = @Content(schema = @Schema(implementation = SolDataResponse.class))),
        @ApiResponse(responseCode = "502", description = "Erreur API SoilGrids", content = @Content)
    })
    @GetMapping("/{id}/sol")
    public ResponseEntity<SolDataResponse> getSolData(@PathVariable Long id) {
        return ResponseEntity.ok(parcelleService.getSolData(id));
    }

    @Operation(summary = "Cultures recommandées et rendement de référence",
               description = "Compare le climat et le sol de la parcelle aux besoins agronomiques de chaque culture (EcoCrop) "
                           + "et donne l'intervalle de rendement FAOSTAT (quartiles) avec la valeur médiane.")
    @GetMapping("/{id}/recommandations")
    public ResponseEntity<RecommandationResponse> getRecommandations(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int limite) {
        return ResponseEntity.ok(parcelleService.getRecommandations(id, Math.min(Math.max(limite, 1), 50)));
    }

    @Operation(summary = "Historique des mesures enregistrées",
               description = "Météo journalière et indices Sentinel stockés en base. Par défaut : 90 derniers jours.")
    @GetMapping("/{id}/historique")
    public ResponseEntity<HistoriqueParcelleResponse> getHistorique(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        LocalDate f = fin != null ? fin : LocalDate.now();
        LocalDate d = debut != null ? debut : f.minusDays(90);
        return ResponseEntity.ok(parcelleService.getHistorique(id, d, f));
    }

    @Operation(
        summary = "Capteurs virtuels (indices satellites)",
        description = "Indices proches de ce que mesureraient des capteurs : humidité du sol de surface " +
                      "(radar Sentinel-1), évapotranspiration ET0 (FAO-56, NASA POWER) et bilan hydrique. " +
                      "Chaque valeur précise son type (indice/calcul), sa confiance et sa méthode. " +
                      "Ce ne sont pas des mesures : ils seront remplacés par un modèle entraîné sur capteurs physiques."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Capteurs virtuels retournés"),
        @ApiResponse(responseCode = "502", description = "Service satellite indisponible", content = @Content)
    })
    @GetMapping("/{id}/capteurs-virtuels")
    public ResponseEntity<java.util.Map<String, Object>> getCapteursVirtuels(@PathVariable Long id) {
        return ResponseEntity.ok(parcelleService.getCapteursVirtuels(id));
    }
}
