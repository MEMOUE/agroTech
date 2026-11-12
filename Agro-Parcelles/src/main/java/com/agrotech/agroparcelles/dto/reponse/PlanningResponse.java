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
    /**
     * Part de la parcelle occupée par cette culture, en % (null si non précisée). Calculée à
     * partir de {@code zoneGps} quand une zone est tracée (≥ 3 sommets), sinon reprise telle que
     * saisie librement par l'utilisateur.
     */
    private Double partSurfacePct;
    /** Superficie correspondante (ha), calculée à partir de la zone tracée ou de {@code partSurfacePct}. */
    private Double superficieAllouee;
    /** Tracé GPS précis de la zone occupée par cette culture sur la parcelle (vide si non tracée). */
    private List<GpsPoint> zoneGps;
    private String statut;

    private List<Tache> taches;

    public record GpsPoint(double lat, double lon) {}

    /**
     * @param statut A_FAIRE | FAIT
     */
    public record Tache(Long id, String type, String libelle, int ordre,
                        LocalDate datePrevue, LocalDate dateReelle, String statut) {}
}
