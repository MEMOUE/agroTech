package com.agrotech.agroparcelles.controller;

import com.agrotech.agroparcelles.dto.reponse.PlanningResponse;
import com.agrotech.agroparcelles.dto.request.ObjectifCultureRequest;
import com.agrotech.agroparcelles.dto.request.ObjectifUpdateRequest;
import com.agrotech.agroparcelles.dto.request.TacheUpdateRequest;
import com.agrotech.agroparcelles.service.ObjectifCultureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parcelles/{parcelleId}/objectifs")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@Tag(name = "Objectifs de récolte & planning", description = "Cultures en cours sur une parcelle (une ou plusieurs à la fois) et leur planning cultural généré automatiquement")
public class ObjectifCultureController {

    private final ObjectifCultureService objectifCultureService;

    @Operation(summary = "Cultures actives de la parcelle et leur planning",
               description = "Liste vide si aucune culture n'est en cours sur cette parcelle.")
    @GetMapping
    public ResponseEntity<List<PlanningResponse>> getPlannings(@PathVariable Long parcelleId) {
        return ResponseEntity.ok(objectifCultureService.getPlannings(parcelleId));
    }

    @Operation(summary = "Ajouter une culture sur la parcelle",
               description = "Génère automatiquement le planning cultural (semis, fertilisation, sarclage, "
                           + "irrigation, récolte...) à partir du cycle de la culture et des dates visées. "
                           + "Les autres cultures déjà en cours sur la parcelle ne sont pas affectées : "
                           + "plusieurs cultures peuvent être suivies en même temps sur une même parcelle.")
    @PostMapping
    public ResponseEntity<PlanningResponse> definirObjectif(
            @PathVariable Long parcelleId, @Valid @RequestBody ObjectifCultureRequest request) {
        return ResponseEntity.ok(objectifCultureService.definirObjectif(parcelleId, request));
    }

    @Operation(summary = "Ajuster le rendement visé et/ou la date de récolte visée d'une culture",
               description = "Ne change pas la culture et ne régénère pas tout le planning ; la tâche "
                           + "« récolte » encore à faire est réalignée sur la nouvelle date si elle change.")
    @PatchMapping("/{objectifId}")
    public ResponseEntity<PlanningResponse> ajusterObjectif(
            @PathVariable Long parcelleId, @PathVariable Long objectifId,
            @Valid @RequestBody ObjectifUpdateRequest request) {
        return ResponseEntity.ok(objectifCultureService.ajusterObjectif(parcelleId, objectifId, request));
    }

    @Operation(summary = "Abandonner une culture",
               description = "La culture n'est plus suivie sur la parcelle ; l'objectif et son planning restent "
                           + "en historique mais ne réapparaissent plus dans la liste des cultures actives.")
    @PostMapping("/{objectifId}/abandonner")
    public ResponseEntity<Void> abandonnerObjectif(@PathVariable Long parcelleId, @PathVariable Long objectifId) {
        objectifCultureService.abandonnerObjectif(parcelleId, objectifId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Cocher/décocher une tâche du planning",
               description = "Si la date réelle diffère de la date prévue, les tâches suivantes encore à faire "
                           + "sont décalées automatiquement du même nombre de jours.")
    @PatchMapping("/{objectifId}/taches/{tacheId}")
    public ResponseEntity<PlanningResponse> marquerTache(
            @PathVariable Long parcelleId, @PathVariable Long objectifId, @PathVariable Long tacheId,
            @Valid @RequestBody TacheUpdateRequest request) {
        return ResponseEntity.ok(objectifCultureService.marquerTache(parcelleId, objectifId, tacheId, request));
    }
}
