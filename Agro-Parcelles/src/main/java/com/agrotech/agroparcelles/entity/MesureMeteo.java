package com.agrotech.agroparcelles.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Mesure agro-climatique journalière (NASA POWER) au centroïde d'une parcelle. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "mesures_meteo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"parcelle_id", "date_mesure"}))
public class MesureMeteo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcelle_id", nullable = false)
    private Long parcelleId;

    @Column(name = "date_mesure", nullable = false)
    private LocalDate dateMesure;

    /** Température moyenne à 2 m (°C) */
    private Double temperature;
    /** Précipitations (mm/j) */
    private Double precipitations;
    /** Humidité relative (%) */
    private Double humiditeRelative;
    /** Rayonnement solaire (MJ/m²/j) */
    private Double rayonnementSolaire;
    /** Vent à 10 m (m/s) */
    private Double vitesseVent;

    @Column(name = "collecte_le", nullable = false)
    private LocalDateTime collecteLe;
}
