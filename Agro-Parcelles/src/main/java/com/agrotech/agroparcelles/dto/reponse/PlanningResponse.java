package com.agrotech.agroparcelles.dto.reponse;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

/** Objectif de récolte d'une parcelle et planning cultural généré à partir de cet objectif. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanningResponse {

    private Long objectifId;
    private Long parcelleId;
    private String culture;
    private Integer cycleJoursMin;
    private Integer cycleJoursMax;
    private LocalDate dateSemisPrevue;
    private LocalDate dateSemisReelle;
    private LocalDate dateRecolteVisee;
    private Double rendementViseTHa;
    private String statut;

    private List<Tache> taches;

    /**
     * @param statut A_FAIRE | FAIT
     */
    public record Tache(Long id, String type, String libelle, int ordre,
                        LocalDate datePrevue, LocalDate dateReelle, String statut) {}
}
