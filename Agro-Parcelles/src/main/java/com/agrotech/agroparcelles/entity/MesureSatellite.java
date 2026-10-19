package com.agrotech.agroparcelles.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Indices Sentinel-2 (NDVI) et Sentinel-1 (radar) calculés sur le polygone de la parcelle,
 * en composite médian sur la fenêtre [periodeDebut, dateMesure]. Un champ reste null quand
 * la source n'a pas fourni de valeur fiable (nuages, aucune scène).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "mesures_satellite",
        uniqueConstraints = @UniqueConstraint(columnNames = {"parcelle_id", "date_mesure"}))
public class MesureSatellite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcelle_id", nullable = false)
    private Long parcelleId;

    /** Fin de la fenêtre de composite. */
    @Column(name = "date_mesure", nullable = false)
    private LocalDate dateMesure;

    @Column(name = "periode_debut", nullable = false)
    private LocalDate periodeDebut;

    private Double ndviMoyen;
    private Double ndviMin;
    private Double ndviMax;
    private Double couvertureNuageusePct;
    private Integer nombreImagesS2;

    private Double vvDb;
    private Double vhDb;
    private Double rvi;
    private Integer nombreImagesS1;

    @Column(name = "collecte_le", nullable = false)
    private LocalDateTime collecteLe;
}
