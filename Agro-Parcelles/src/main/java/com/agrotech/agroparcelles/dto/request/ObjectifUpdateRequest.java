package com.agrotech.agroparcelles.dto.request;

import jakarta.validation.Valid;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Ajustement d'un objectif existant (rendement visé, date de récolte visée, part de surface et/ou
 * zone tracée) sans toucher à la culture ni régénérer tout le planning. La tâche "récolte" encore
 * à faire est alignée sur la nouvelle date de récolte si celle-ci change.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ObjectifUpdateRequest {

    private LocalDate dateRecolteVisee;

    private Double rendementViseTHa;

    /** Part de la parcelle occupée par cette culture, en % (0-100) ; ignoré si {@code zoneGps} est tracée. */
    private Double partSurfacePct;

    /** Tracé précis de la zone occupée (≥ 3 sommets) ; la part de surface est alors recalculée automatiquement. */
    @Valid
    private List<ParcelleDtoRequest.GpsPointRequest> zoneGps;
}
