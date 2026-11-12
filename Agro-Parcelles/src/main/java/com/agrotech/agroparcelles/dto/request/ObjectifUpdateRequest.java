package com.agrotech.agroparcelles.dto.request;

import lombok.*;

import java.time.LocalDate;

/**
 * Ajustement d'un objectif existant (rendement visé, date de récolte visée et/ou part de la
 * parcelle occupée) sans toucher à la culture ni régénérer tout le planning. La tâche "récolte"
 * encore à faire est alignée sur la nouvelle date de récolte si celle-ci change.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ObjectifUpdateRequest {

    private LocalDate dateRecolteVisee;

    private Double rendementViseTHa;

    /** Part de la parcelle occupée par cette culture, en % (0-100) ; optionnel. */
    private Double partSurfacePct;
}
