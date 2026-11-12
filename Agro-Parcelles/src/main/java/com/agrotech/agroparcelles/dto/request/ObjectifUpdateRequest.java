package com.agrotech.agroparcelles.dto.request;

import lombok.*;

import java.time.LocalDate;

/**
 * Ajustement d'un objectif existant (rendement visé et/ou date de récolte visée) sans toucher à
 * la culture ni régénérer tout le planning. La tâche "récolte" encore à faire est alignée sur la
 * nouvelle date de récolte si celle-ci change.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ObjectifUpdateRequest {

    private LocalDate dateRecolteVisee;

    private Double rendementViseTHa;
}
